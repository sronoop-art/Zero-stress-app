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
const crypto = require("crypto");

const DAY_MS = 24 * 60 * 60 * 1000;

// ------------------------------------------------------------- Web Push ----
// Web/PWA users (web/index.html) store their push subscription as a JSON
// string on players/{uid}.webPush. Messages are delivered with the standard
// Web Push protocol (VAPID), which is FREE - it goes browser -> push service
// (Apple/Google/Mozilla endpoints) directly, no FCM project plumbing needed.
// Set these two repository secrets to enable:
//   ZS_WEB_PUSH_PUBLIC_KEY  - base64url public key from the VAPID key pair
//   ZS_WEB_PUSH_PRIVATE_KEY - base64url private key from the same pair
// (Generate once: Firebase Console > Project settings > Cloud Messaging >
//  Web Push certificates > Generate key pair.)
function webPushConfigured() {
  return !!(process.env.ZS_WEB_PUSH_PUBLIC_KEY && process.env.ZS_WEB_PUSH_PRIVATE_KEY);
}

async function webPushSend(subJson, title, message, type, threadId) {
  try {
    const sub = JSON.parse(subJson);
    const endpoint = sub.endpoint;
    const peer = sub.keys && sub.keys.p256dh;
    if (!endpoint || !peer) return "bad-sub";
    const payloadJson = {
      endpointOrigin: new URL(endpoint).origin,
      body: JSON.stringify({ title, body: message, type, threadId })
    };
    const built = webPushRequest(peer, payloadJson);
    const res = await fetch(endpoint, { method: "POST", headers: built.headers, body: built.body });
    if (res.status === 404 || res.status === 410) return "dead-sub";
    if (!res.ok) {
      console.log("web push failed: HTTP " + res.status);
      return "failed";
    }
    return "sent";
  } catch (e) {
    console.log("web push error: " + (e.message || e));
    return "failed";
  }
}

// Builds the full encrypted aes128gcm POST for one subscription.
// peerU64 = the subscription's p256dh key (the USER's ECDH P-256 public key).
function webPushRequest(peerU64, payloadJson) {
  const pub = process.env.ZS_WEB_PUSH_PUBLIC_KEY;
  const priv = process.env.ZS_WEB_PUSH_PRIVATE_KEY;
  const b64u = (buf) => Buffer.from(buf).toString("base64url");

  const ecdh = crypto.createECDH("prime256v1");
  ecdh.generateKeys();
  const pubSrv = ecdh.getPublicKey();
  const peer = Buffer.from(peerU64, "base64url");
  const shared = ecdh.computeSecret(peer);

  const jwtHeader = b64u(JSON.stringify({ typ: "JWT", alg: "ES256" }));
  const jwtClaims = b64u(JSON.stringify({
    aud: payloadJson.endpointOrigin,
    exp: Math.floor(Date.now() / 1000) + 12 * 3600,
    sub: "mailto:admin@zerostress.app"
  }));
  const signer = crypto.createSign("SHA256");
  signer.update(Buffer.from(jwtHeader + "." + jwtClaims));
  const der = signer.sign(vapidPrivateKeyPem(priv));
  let r = der.subarray(4, 4 + der[3]);
  let s = der.subarray(6 + der[3]);
  while (r.length < 32) r = Buffer.concat([Buffer.from([0]), r]);
  while (s.length < 32) s = Buffer.concat([Buffer.from([0]), s]);
  const sig = Buffer.concat([r.subarray(0, 32), s.subarray(0, 32)]);
  const jwt = jwtHeader + "." + jwtClaims + "." + b64u(sig);

  const salt = crypto.randomBytes(16);
  const cekInfo = Buffer.concat([
    Buffer.from("Content-Encoding: aes128gcm\x00"), Buffer.from("P-256"),
    Buffer.from([0]), Buffer.from([0, 65]),
    Buffer.from("P-256"), Buffer.from([0, 65])
  ]);
  const nonceInfo = Buffer.concat([
    Buffer.from("Content-Encoding: nonce\x00"), Buffer.from("P-256"),
    Buffer.from([0]), Buffer.from([0, 65]),
    Buffer.from("P-256"), Buffer.from([0, 65])
  ]);
  const ikm = crypto.hkdfSync("sha256", shared, salt, cekInfo, 16);
  const cek = crypto.createSecretKey(Buffer.from(ikm));
  const iv = Buffer.from(crypto.hkdfSync("sha256", shared, salt, nonceInfo, 12));

  const payload = Buffer.from(payloadJson.body, "utf8");
  const cipher = crypto.createCipheriv("aes-128-gcm", cek, iv);
  const ct = Buffer.concat([cipher.update(payload), cipher.final(), cipher.getAuthTag()]);
  const header = Buffer.concat([
    salt,
    Buffer.from([0x00, 0x00, 0x10, 0x00]),
    Buffer.from([65]),
    pubSrv
  ]);
  return {
    headers: {
      "Content-Encoding": "aes128gcm",
      "Content-Type": "application/octet-stream",
      TTL: "86400",
      Authorization: "vapid t=" + jwt + ", k=" + pub
    },
    body: Buffer.concat([header, ct])
  };
}

async function webPushToUid(uid, title, message, type, threadId) {
  if (!webPushConfigured()) return "disabled";
  const d = getDb();
  const player = await d.collection("players").doc(uid).get();
  const subJson = player.exists ? player.data().webPush : null;
  if (!subJson || typeof subJson !== "string") return "no-sub";
  const result = await webPushSend(subJson, title, message, type, threadId);
  if (result === "dead-sub") {
    d.collection("players").doc(uid).update({ webPush: admin.firestore.FieldValue.delete() }).catch(() => {});
  }
  return result;
}

async function webPushToAll(title, message, type) {
  if (!webPushConfigured()) return "disabled";
  const d = getDb();
  const players = await d.collection("players").get();
  let sent = 0;
  for (const doc of players.docs) {
    const subJson = doc.data().webPush;
    if (subJson && typeof subJson === "string") {
      const r = await webPushSend(subJson, title, message, type, null);
      if (r === "sent") sent++;
      if (r === "dead-sub") {
        d.collection("players").doc(doc.id).update({ webPush: admin.firestore.FieldValue.delete() }).catch(() => {});
      }
    }
  }
  if (sent) console.log("Web push broadcast: " + sent + " subscriber(s)");
  return "done";
}

// Wraps a raw 32-byte base64url private key in a proper SEC1
// "EC PRIVATE KEY" PEM (ASN.1 DER), which is what VAPID key pairs give you.
function vapidPrivateKeyPem(privB64u) {
  const priv = Buffer.from(privB64u.replace(/-/g, "+").replace(/_/g, "/"), "base64");
  if (priv.length !== 32) throw new Error("VAPID private key must be 32 bytes");
  // SEQUENCE(49) { INTEGER 1, OCTET STRING(32) privateKey, [0] OID prime256v1 }
  const der = Buffer.concat([
    Buffer.from([0x30, 0x31, 0x02, 0x01, 0x01, 0x04, 0x20]),
    priv,
    Buffer.from([0xA0, 0x0A, 0x06, 0x08, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x03, 0x01, 0x07])
  ]);
  const b64 = der.toString("base64").replace(/(.{64})/g, "$1\n");
  return "-----BEGIN EC PRIVATE KEY-----\n" + b64 + "\n-----END EC PRIVATE KEY-----";
}

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
  // Web/PWA users get the same notification through Web Push (free, no FCM).
  await webPushToUid(uid, title, message, type, extraData ? extraData.threadId || null : null).catch(() => {});
  if ((process.env.FCM_V1_ENABLED || "true").toLowerCase() === "false") {
    console.log("FCM_V1_ENABLED=false - push skipped (in-app notification only).");
    return "skipped";
  }
  const d = getDb(); // also initializes the app for admin.messaging()
  const player = await d.collection("players").doc(uid).get();
  const token = player.exists ? player.data().fcmToken : null;
  if (!token) {
    // The usual cause: the device has no players/{uid} doc yet, so the app
    // could not store its FCM token (run scripts/backfill-players.cjs), or the
    // app has not been opened since the doc was created.
    console.log(
      `no device token for ${uid} ` +
        `(${player.exists ? "player doc has no fcmToken - open the app once" : "no player doc - backfill players"})`
    );
    return "no-token";
  }
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
    return "sent";
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
    return "failed";
  }
}

// Broadcast push: fan out to every registered device token.
// Topic sends are cheaper, but a device that never subscribed to
// "all_players" would silently miss the announcement, so we address tokens
// directly (same approach as the Cloud Function version).
async function sendToAll(title, message, type) {
  await webPushToAll(title, message, type).catch(() => {});
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
  else if (snap.docs.length) console.log("Push relay had nothing new to send.");
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
