// Free-plan replacement for Cloud Functions: runs as a scheduled GitHub Action.
// Sends pushes via the modern FCM HTTP v1 API through the firebase-admin SDK —
// OAuth tokens are minted automatically from the service account, so NO
// legacy FCM_SERVER_KEY is needed (that API is being retired by Google).
//
// Required GitHub secrets:
//   FIREBASE_SERVICE_ACCOUNT - full service-account JSON (already configured).
//                              Used for BOTH Firestore access and push sends.
//
// Optional env override: FCM_V1_ENABLED=false disables pushes (in-app
// notifications and resets still run).

const admin = require("firebase-admin");

const DAY_MS = 24 * 60 * 60 * 1000;

// ---------------------------------------------------------------- Firestore
let db = null;
function getDb() {
  if (db) return db;
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) throw new Error("FIREBASE_SERVICE_ACCOUNT env var is not set");
  const sa = JSON.parse(raw);
  admin.initializeApp({ credential: admin.credential.cert(sa) });
  db = admin.firestore();
  return db;
}

// ------------------------------------------------------------- FCM (HTTP v1)
// firebase-admin mints the OAuth token from the service account and posts to
// the v1 endpoint — same free FCM delivery, no legacy server key involved.
// When the app is in the foreground our own listener picks the channel, but for
// a backgrounded/killed app the SYSTEM draws the push. It only uses a
// high-importance channel (status bar + heads-up) when the message names one.
function channelFor(type) {
  if (type === "chat" || type === "mention") return "zs_chat";
  if (type === "schedule") return "zs_schedule";
  return "zs_notifications";
}

async function sendToUid(uid, title, message, type, extraData) {
  if ((process.env.FCM_V1_ENABLED || "true").toLowerCase() === "false") {
    console.log("FCM_V1_ENABLED=false - push skipped (in-app notification only).");
    return;
  }
  const d = getDb(); // also initializes the app for admin.messaging()
  const player = await d.collection("players").doc(uid).get();
  const token = player.exists ? player.data().fcmToken : null;
  if (!token) return; // no device token / disabled
  // v1 requires all data payload values to be strings.
  const raw = Object.assign({ type: type || "general", uid: uid }, extraData || {});
  const data = {};
  for (const k of Object.keys(raw)) data[k] = String(raw[k]);
  try {
    await admin.messaging().send({
      token: token,
      notification: { title: title, body: message },
      data: data,
      android: {
        priority: "high",
        notification: { channelId: channelFor(type), priority: "high" },
      },
    });
    console.log(`Push to ${uid}: sent`);
  } catch (err) {
    const code = err && err.code ? String(err.code) : "";
    if (
      code === "messaging/registration-token-not-registered" ||
      code === "messaging/invalid-registration-token"
    ) {
      // Token no longer valid - clean it up.
      d.collection("players").doc(uid)
        .update({ fcmToken: admin.firestore.FieldValue.delete() }).catch(() => {});
      console.log(`Push to ${uid}: token invalid, removed`);
    } else {
      console.log(`Push to ${uid} failed: ${err.message || code}`);
    }
  }
}

// Broadcast push: fan out to every registered device token.
// Topic sends are cheaper, but a device that never subscribed to
// "all_players" would silently miss the announcement, so we address tokens
// directly (same approach as the Cloud Function version).
async function sendToAll(title, message, type) {
  if ((process.env.FCM_V1_ENABLED || "true").toLowerCase() === "false") {
    console.log("FCM_V1_ENABLED=false - broadcast push skipped.");
    return;
  }
  const d = getDb();
  const players = await d.collection("players").get();
  const devices = [];
  for (const doc of players.docs) {
    const token = doc.data().fcmToken;
    if (token && typeof token === "string" && token.length > 0) {
      devices.push({ uid: doc.id, token: token });
    }
  }
  if (devices.length === 0) {
    console.log("Broadcast: no device tokens registered yet");
    return;
  }
  // v1 requires all data payload values to be strings.
  const data = { type: String(type || "general") };
  let sent = 0;
  for (let i = 0; i < devices.length; i += 500) {
    const batch = devices.slice(i, i + 500);
    try {
      const res = await admin.messaging().sendEachForMulticast({
        tokens: batch.map((x) => x.token),
        notification: { title: title, body: message },
        data: data,
        android: {
          priority: "high",
          notification: { channelId: channelFor(type), priority: "high" },
        },
      });
      sent += res.successCount;
      const dead = [];
      res.responses.forEach((r, idx) => {
        if (r.success) return;
        const code = r.error && r.error.code ? String(r.error.code) : "";
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-registration-token"
        ) {
          dead.push(batch[idx].uid);
        }
      });
      for (const uid of dead) {
        await d.collection("players").doc(uid)
          .update({ fcmToken: admin.firestore.FieldValue.delete() })
          .catch(() => {});
      }
    } catch (err) {
      console.log(`Broadcast batch failed: ${err.message || err}`);
    }
  }
  console.log(`Broadcast push: ${sent} device(s) notified`);
}

// --------------------------------------------- push queue (relay for app)
// The app writes chat/mention/admin notifications into the "notifications"
// collection. Docs with a "uid" value are targeted pushes; docs with
// "uid: null" (admin broadcast) go to every device. Each doc is flagged
// pushSent so it is never sent twice.
async function processPushQueue() {
  const d = getDb();
  const cutoff = Date.now() - 3 * DAY_MS; // ignore stale docs (e.g. first run)
  // orderBy("uid") excludes docs that lack the field entirely - every app
  // writer sets it (a uid for targeted pushes, an explicit null for admin
  // broadcasts), so both kinds are relayed here.
  const snap = await d.collection("notifications")
    .orderBy("uid")
    .orderBy("timestamp", "desc")
    .limit(50)
    .get();
  let sent = 0;
  for (const doc of snap.docs) {
    const data = doc.data();
    if (data.pushSent) continue;
    if (!data.uid) {
      // Admin broadcast (uid: null). The Cloud Function path pushes these on
      // Blaze; on the free Spark plan this relay is the only sender, so it must
      // push here too - otherwise "SEND TO ALL PLAYERS" reached nobody's status
      // bar. push:false stays deliberately in-app only.
      if (data.push !== false) {
        await sendToAll(data.title || "ZERO STRESS", data.message || "", data.type || "general");
      }
      doc.ref.update({ pushSent: true }).catch(() => {});
      continue;
    }
    if ((data.timestamp || 0) < cutoff) {
      doc.ref.update({ pushSent: true }).catch(() => {}); // expire old, no push
      continue;
    }
    await sendToUid(
      data.uid,
      data.title || "Zero Stress",
      data.message || "",
      data.type || "general",
      data.scheduleId ? { scheduleId: data.scheduleId } : null
    );
    await doc.ref.update({ pushSent: true });
    sent++;
  }
  if (sent) console.log(`Push relay sent ${sent} notification(s)`);
}

// ---------------------------------------------------------- match reminders
async function matchReminders(now) {
  const d = getDb();
  const windowEnd = now + 15 * 60 * 1000;
  const snap = await d.collection("match_schedules")
    .where("status", "==", "Upcoming")
    .where("matchTime", ">=", now - DAY_MS)
    .where("matchTime", "<=", windowEnd)
    .get();
  for (const doc of snap.docs) {
    if (doc.data().reminderSent) continue;
    const data = doc.data();
    const uids = data.playerUids || [];
    if (uids.length) {
      for (const uid of uids) {
        await sendToUid(
          uid,
          "Match starting soon: " + (data.title || "Match"),
          `${data.type || "Match"} starts at ${data.dateTime || "soon"}. Get ready!`,
          "schedule",
          { scheduleId: doc.id }
        );
      }
    } else {
      // No per-player list on the match: write an in-app-only notification
      // (push:false). Without the flag, the event-triggered Cloud Function
      // treats a uid-less doc as a broadcast and status-bar-pushes EVERY
      // player for matches they are not in.
      await d.collection("notifications").add({
        title: "Match starting soon: " + (data.title || "Match"),
        message: `${data.type || "Match"} starts at ${data.dateTime || "soon"}. Get ready!`,
        type: "schedule",
        timestamp: now,
        scheduleId: doc.id,
        push: false,
      });
    }
    await doc.ref.update({ reminderSent: true });
    console.log("Reminder sent for match", doc.id);
  }
}

// ------------------------------------------------------- season auto-reset
async function autoSeasonReset(now) {
  const d = getDb();
  const seasons = await d.collection("seasons").where("active", "==", true).get();
  for (const season of seasons.docs) {
    const data = season.data();
    const created = data.createdAt || 0;
    const days = parseInt(data.duration || "30", 10) || 30;
    if (created > 0 && now - created > days * DAY_MS) {
      const top = await d.collection("players")
        .where("status", "==", "approved")
        .orderBy("score", "desc").limit(3).get();
      const rewards = [data.topRewardCoins || 500, 300, 150];
      let rank = 0;
      for (const p of top.docs) {
        const coins = (p.data().coins || 0) + (rewards[rank] || 0);
        await p.ref.update({ coins: coins });
        await sendToUid(
          p.id,
          "Season ended - you placed #" + (rank + 1) + "!",
          `You earned ${rewards[rank] || 0} coins in ${data.name || "the season"}.`,
          "achievement"
        );
        await d.collection("notifications").add({
          uid: p.id,
          title: "Season ended - you placed #" + (rank + 1) + "!",
          message: `You earned ${rewards[rank] || 0} coins in ${data.name || "the season"}.`,
          type: "achievement",
          timestamp: now,
        });
        rank++;
      }
      await season.ref.update({ active: false, endedAt: now });
      console.log(`Season ${season.id} ended; top players rewarded`);
    }
  }
  // Ensure an active season exists.
  const active = await d.collection("seasons").where("active", "==", true).get();
  if (active.size === 0) {
    const total = await d.collection("seasons").get();
    await d.collection("seasons").add({
      name: "Season " + (total.size + 1),
      description: "Auto-created season",
      duration: "30",
      active: true,
      createdAt: now,
    });
    console.log("New season auto-created");
  }
}

// ------------------------------------------------------- leaderboard resets
async function resetFields(fieldScore, fieldWins, fieldKills) {
  const d = getDb();
  const snap = await d.collection("players").where("status", "==", "approved").get();
  let batch = d.batch();
  let ops = 0;
  for (const doc of snap.docs) {
    const update = {};
    update[fieldScore] = 0;
    update[fieldWins] = 0;
    if (fieldKills) update[fieldKills] = 0;
    batch.update(doc.ref, update);
    ops++;
    if (ops === 400) {
      await batch.commit();
      batch = d.batch();
      ops = 0;
    }
  }
  if (ops > 0) await batch.commit();
}

async function resetLeaderboards(now) {
  const jobs = [resetFields("dailyScore", "dailyWins", "dailyKills")];
  if (new Date(now).getUTCDay() === 1) {
    jobs.push(resetFields("weeklyScore", "weeklyWins", "weeklyKills"));
  }
  if (new Date(now).getUTCDate() === 1) {
    jobs.push(resetFields("monthlyScore", "monthlyWins", "monthlyKills"));
  }
  await Promise.all(jobs);
  console.log("Leaderboard reset done");
}

// --------------------------------------------------------------------- main
async function main() {
  const now = Date.now();
  const which = process.env.CRON_JOB || "all";
  // The push queue is drained on every run except leaderboard resets.
  if (which !== "reset") {
    await processPushQueue();
  }
  if (which === "all" || which === "reminders") {
    await matchReminders(now);
  }
  if (which === "all" || which === "season") {
    await autoSeasonReset(now);
  }
  if (which === "all" || which === "reset") {
    await resetLeaderboards(now);
  }
  console.log("Cron run complete.");
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error("Cron run failed:", err.message || err);
    process.exit(1);
  }
);
