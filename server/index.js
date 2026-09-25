// ZERO STRESS - always-on Firestore -> Knock push bridge.
//
// Why this exists: the Firebase project is on the free Spark plan, so Cloud
// Functions (Firestore triggers) cannot be deployed and outbound networking
// from them is unavailable. The checked-in GitHub Actions relay can only poll
// every 30 minutes, which is the latency people complain about. This service
// listens to the `notifications` collection in real time and triggers the
// Knock workflow the moment a document is written, so a push arrives in
// seconds. The 30-minute relay stays as a fallback for when this service is
// offline (both paths share the `pushSent` flag, so nothing is sent twice).
//
// The Android app needs no changes: it already writes notifications with
// `uid` (targeted) or `uid: null` (broadcast) and already saves its FCM token
// to players/{uid}.fcmToken, which this service reads and passes inline to
// Knock as channel data. No Knock secret ever reaches the APK.
//
// Required environment variables:
//   KNOCK_API_KEY            - Knock secret API key (never ship in the APK)
//   FIREBASE_SERVICE_ACCOUNT - Firebase service-account JSON, base64 of it,
//                              or a path to the .json file. Optional only when
//                              GOOGLE_APPLICATION_CREDENTIALS is set instead.
//
// Optional:
//   KNOCK_WORKFLOW_KEY       - defaults to "zs-push"
//   KNOCK_FCM_CHANNEL_ID     - the Knock FCM channel UUID
//   KNOCK_API_BASE           - defaults to https://api.knock.app
//   DIRECT_FCM_FALLBACK      - "false" disables the direct FCM send used when
//                              Knock is unreachable (default: enabled)
//   RECONCILE_WINDOW_MINUTES - catch-up window on restart, default 10
//   PORT                     - health-check port, default 8080
//
// Health: GET /health (+ GET /) reports readiness and the last send/error.

"use strict";

const fs = require("fs");
const http = require("http");
const admin = require("firebase-admin");

const {
  buildTriggerData,
  recipientFor,
  chunk,
  stringifyValues,
  deviceTokens,
  parseServiceAccount,
  describeServiceAccount,
} = require("./lib");

const KNOCK_API_KEY = process.env.KNOCK_API_KEY;
const KNOCK_WORKFLOW_KEY = process.env.KNOCK_WORKFLOW_KEY || "zs-push";
const KNOCK_FCM_CHANNEL_ID =
  process.env.KNOCK_FCM_CHANNEL_ID || "c35285c4-1fb1-4b0c-b9ea-cb662dd7988b";
const KNOCK_API_BASE = (process.env.KNOCK_API_BASE || "https://api.knock.app").replace(/\/+$/, "");
const DIRECT_FCM_FALLBACK = (process.env.DIRECT_FCM_FALLBACK || "true").toLowerCase() !== "false";
const RECONCILE_WINDOW_MS =
  (Number(process.env.RECONCILE_WINDOW_MINUTES) || 10) * 60 * 1000;

// Knock's trigger API accepts at most 1000 recipients per call.
const BROADCAST_BATCH_SIZE = 1000;
// Enough to reconcile a short restart window without loading the collection.
const LISTEN_LIMIT = 200;

const state = {
  startedAt: Date.now(),
  listening: false,
  sent: 0,
  lastEventAt: null,
  lastSentAt: null,
  lastError: null,
};

if (!KNOCK_API_KEY) {
  console.error("KNOCK_API_KEY is not set; refusing to start.");
  process.exit(1);
}

function initFirebase() {
  const sa = parseServiceAccount(process.env.FIREBASE_SERVICE_ACCOUNT, (path) =>
    fs.readFileSync(path, "utf8")
  );
  console.log(
    sa
      ? `Firebase credentials: ${describeServiceAccount(sa)}`
      : "Firebase credentials: application default credentials"
  );
  admin.initializeApp(
    sa ? { credential: admin.credential.cert(sa) } : undefined
  );
  return admin.firestore();
}

const db = initFirebase();
const notificationsRef = db.collection("notifications");

// ------------------------------------------------------------------ Knock

async function triggerKnock(recipients, data, idempotencyKey) {
  const response = await fetch(
    `${KNOCK_API_BASE}/v1/workflows/${encodeURIComponent(KNOCK_WORKFLOW_KEY)}/trigger`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${KNOCK_API_KEY}`,
        "Content-Type": "application/json",
        // Same notification document => same key, so a retry or a double
        // delivery can never notify a device twice (Knock dedupes for 24h).
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify({ recipients, data }),
      signal: AbortSignal.timeout(15000),
    }
  );

  const text = await response.text();
  if (!response.ok) {
    throw new Error(`Knock trigger failed (${response.status}): ${text.slice(0, 500)}`);
  }
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

// ------------------------------------------------------------- Recipients

async function tokensForUid(uid) {
  const snap = await db.collection("players").doc(uid).get();
  if (!snap.exists) return [];
  return deviceTokens([{ uid, data: snap.data() }]);
}

async function activeDeviceTokens() {
  const snapshot = await db.collection("players").get();
  return deviceTokens(snapshot.docs.map((doc) => ({ uid: doc.id, data: doc.data() })));
}

async function forgetToken(uid) {
  await db.collection("players").doc(uid)
    .update({ fcmToken: admin.firestore.FieldValue.delete() })
    .catch(() => {});
}

// Direct FCM is the safety net for a Knock outage (bad/missing workflow key,
// channel not configured, API down). It is the exact send path the GitHub
// Actions relay has been using on the Spark plan, so it is known to work.
async function sendDirectFcm(devices, data) {
  const payload = stringifyValues(data);
  let delivered = 0;
  for (const batch of chunk(devices, 500)) {
    try {
      const result = await admin.messaging().sendEachForMulticast({
        tokens: batch.map((device) => device.token),
        notification: { title: payload.title, body: payload.body },
        data: payload,
        android: {
          priority: "high",
          notification: { channelId: payload.channelId, priority: "high" },
        },
      });
      delivered += result.successCount;
      const dead = [];
      result.responses.forEach((response, index) => {
        const code = response.error && response.error.code ? String(response.error.code) : "";
        if (
          !response.success &&
          (code === "messaging/registration-token-not-registered" ||
            code === "messaging/invalid-registration-token")
        ) {
          dead.push(batch[index].uid);
        }
      });
      for (const uid of dead) await forgetToken(uid);
    } catch (error) {
      console.error(`Direct FCM batch failed: ${error.message || error}`);
    }
  }
  return delivered;
}

// ------------------------------------------------------- One notification

/**
 * Deliver one `notifications/{id}` document.
 * Returns a short status string (used in the logs only).
 *
 * Mirrors functions/cron.js `processPushQueue` semantics so both senders agree:
 *   pushSent: true  -> already delivered, skip
 *   push: false     -> in-app only, mark sent so the relay does not push it
 *   uid: <id>       -> targeted  |  uid: null -> broadcast to every device
 *   no token yet    -> leave pushSent unset so the relay retries later
 */
async function processNotification(docId, data) {
  state.lastEventAt = Date.now();

  if (data.pushSent === true) return "already-sent";

  const docRef = notificationsRef.doc(docId);

  if (data.push === false) {
    // In-app only (a match reminder with no player list is the usual case).
    // Flag it so the 30-minute relay does not turn it into a push either.
    await docRef.update({ pushSent: true }).catch(() => {});
    return "in-app-only";
  }

  const targetUid = typeof data.uid === "string" && data.uid ? data.uid : null;
  const devices = targetUid ? await tokensForUid(targetUid) : await activeDeviceTokens();
  if (devices.length === 0) {
    // No device to reach (yet). pushSent stays false on purpose: the
    // 30-minute relay will pick it up once a token exists.
    console.log(`[skip] ${docId}: no FCM token ${targetUid ? `for ${targetUid}` : "registered"}`);
    return "no-token";
  }

  const triggerData = buildTriggerData(docId, data, targetUid);
  const batches = chunk(devices, BROADCAST_BATCH_SIZE);

  try {
    let runIds = [];
    for (let index = 0; index < batches.length; index++) {
      const recipients = batches[index].map((device) =>
        recipientFor(KNOCK_FCM_CHANNEL_ID, device.uid, device.token)
      );
      const suffix = batches.length > 1 ? `:batch:${index}` : "";
      const run = await triggerKnock(recipients, triggerData, `${docId}${suffix}`);
      if (run && run.workflow_run_id) runIds.push(run.workflow_run_id);
    }
    await docRef.update({
      pushSent: true,
      pushSentVia: "knock",
      knockTriggeredAt: Date.now(),
      knockWorkflowKey: KNOCK_WORKFLOW_KEY,
      ...(runIds.length ? { knockWorkflowRunId: runIds[0] } : {}),
    });
    state.sent++;
    state.lastSentAt = Date.now();
    state.lastError = null;
    console.log(`[sent] ${docId}: ${devices.length} recipient(s) via Knock`);
    return "sent";
  } catch (error) {
    const message = error && error.message ? error.message : String(error);
    state.lastError = `${new Date().toISOString()} ${message}`;
    console.error(`[error] ${docId}: ${message}`);
    if (!DIRECT_FCM_FALLBACK) return "knock-failed";
  }

  const delivered = await sendDirectFcm(devices, triggerData);
  if (delivered === 0) {
    console.log(`[retry-later] ${docId}: Knock and direct FCM both failed`);
    return "failed";
  }
  await docRef.update({
    pushSent: true,
    pushSentVia: "direct-fcm",
    knockTriggeredAt: Date.now(),
  }).catch(() => {});
  state.sent++;
  state.lastSentAt = Date.now();
  console.log(`[sent] ${docId}: ${delivered} recipient(s) via direct FCM fallback`);
  return "sent-fallback";
}

// ------------------------------------------------------------- Listener

const inFlight = new Set();
let initialSnapshotDone = false;
const subscribedAt = Date.now();

function enqueue(docId, data) {
  if (inFlight.has(docId)) return;
  inFlight.add(docId);
  processNotification(docId, data)
    .catch((error) => {
      state.lastError = `${new Date().toISOString()} ${error.message || error}`;
      console.error(`[error] ${docId}: ${error.message || error}`);
    })
    .finally(() => inFlight.delete(docId));
}

function startListener() {
  return notificationsRef
    .orderBy("timestamp", "desc")
    .limit(LISTEN_LIMIT)
    .onSnapshot(
      (snapshot) => {
        // The first callback replays documents that already existed, so on that
        // one we only catch up on the recent window. Every later "added" change
        // is by definition new, and is never compared against a device clock -
        // a phone with a skewed clock can no longer make its own notification
        // disappear.
        const isInitial = !initialSnapshotDone;
        initialSnapshotDone = true;
        state.listening = true;

        for (const change of snapshot.docChanges()) {
          if (change.type !== "added") continue;
          const data = change.doc.data() || {};
          if (
            isInitial &&
            !(typeof data.timestamp === "number" && data.timestamp >= subscribedAt - RECONCILE_WINDOW_MS)
          ) {
            continue;
          }
          enqueue(change.doc.id, data);
        }
        if (isInitial) {
          console.log(
            `Listening for new notifications (reconciled the last ${Math.round(
              RECONCILE_WINDOW_MS / 60000
            )} minute(s))`
          );
        }
      },
      (error) => {
        state.listening = false;
        state.lastError = `${new Date().toISOString()} listener: ${error.message || error}`;
        console.error("Firestore listener failed:", error.message || error);
      }
    );
}

// ---------------------------------------------------------------- Health

const listener = startListener();

const server = http.createServer((req, res) => {
  if (req.url === "/health" || req.url === "/") {
    const body = {
      status: state.listening ? "ok" : "degraded",
      workflowKey: KNOCK_WORKFLOW_KEY,
      channelConfigured: Boolean(KNOCK_FCM_CHANNEL_ID),
      directFcmFallback: DIRECT_FCM_FALLBACK,
      listenerAttached: state.listening,
      uptimeSeconds: Math.round((Date.now() - state.startedAt) / 1000),
      sent: state.sent,
      lastSentAt: state.lastSentAt,
      lastError: state.lastError,
    };
    res.writeHead(state.listening ? 200 : 503, { "Content-Type": "application/json" });
    res.end(JSON.stringify(body));
    return;
  }
  res.writeHead(404);
  res.end();
});

const port = Number(process.env.PORT || 8080);
server.listen(port, "0.0.0.0", () => {
  console.log(`Knock bridge up. Health check on :${port}/health, workflow "${KNOCK_WORKFLOW_KEY}".`);
});

function shutdown() {
  console.log("Shutting down.");
  try {
    listener();
  } catch {
    /* already detached */
  }
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 5000).unref();
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);
