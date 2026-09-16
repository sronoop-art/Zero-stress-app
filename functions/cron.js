// Free-plan replacement for Cloud Functions: runs as a scheduled GitHub Action.
// Uses the legacy FCM HTTP API (one server key env var, no OAuth for FCM) and
// Firestore via the firebase-admin SDK with the existing service-account secret.
//
// Required GitHub secrets:
//   FIREBASE_SERVICE_ACCOUNT - full service-account JSON (already configured)
//   FCM_SERVER_KEY           - legacy server key (Firebase Console -> Project
//                              settings -> Cloud Messaging -> Server key).
//                              If missing, pushes are skipped (in-app
//                              notifications and resets still run).

const admin = require("firebase-admin");
const https = require("https");

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

// ------------------------------------------------------------- FCM (legacy)
function fcmSend(key, payload) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(payload);
    const req = https.request(
      {
        hostname: "fcm.googleapis.com",
        path: "/fcm/send",
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(data),
          Authorization: "key=" + key,
        },
      },
      (res) => {
        let buf = "";
        res.on("data", (c) => (buf += c));
        res.on("end", () => resolve({ status: res.statusCode, body: buf }));
      }
    );
    req.on("error", reject);
    req.write(data);
    req.end();
  });
}

async function sendToUid(uid, title, message, type, extraData) {
  const key = process.env.FCM_SERVER_KEY;
  if (!key) {
    console.log("FCM_SERVER_KEY not set - push skipped (in-app notification only).");
    return;
  }
  const d = getDb();
  const player = await d.collection("players").doc(uid).get();
  const token = player.exists ? player.data().fcmToken : null;
  if (!token) return; // no device token / disabled
  try {
    const res = await fcmSend(key, {
      to: token,
      priority: "high",
      notification: { title: title, body: message },
      data: Object.assign({ type: type || "general", uid: uid }, extraData || {}),
    });
    if (res.status === 404 || res.status === 410) {
      // Token no longer valid - clean it up.
      d.collection("players").doc(uid)
        .update({ fcmToken: admin.firestore.FieldValue.delete() }).catch(() => {});
    }
    console.log(`Push to ${uid}: HTTP ${res.status}`);
  } catch (err) {
    console.log(`Push to ${uid} failed: ${err.message}`);
  }
}

// --------------------------------------------- push queue (relay for app)
// The app writes chat/mention/admin notifications into the "notifications"
// collection. Docs with a "uid" field are targeted; the relay sends each
// unsent one as a status-bar push and flags it pushSent so it never repeats.
async function processPushQueue() {
  const d = getDb();
  const cutoff = Date.now() - 3 * DAY_MS; // ignore stale docs (e.g. first run)
  // orderBy("uid") excludes docs without the field; explicit null values
  // (broadcasts) are skipped and marked so they are never re-fetched.
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
      doc.ref.update({ pushSent: true }).catch(() => {}); // broadcast: in-app only
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
      await d.collection("notifications").add({
        title: "Match starting soon: " + (data.title || "Match"),
        message: `${data.type || "Match"} starts at ${data.dateTime || "soon"}. Get ready!`,
        type: "schedule",
        timestamp: now,
        scheduleId: doc.id,
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
