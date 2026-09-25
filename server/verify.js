// One-command end-to-end check for the Knock wiring:
//
//   cd server
//   KNOCK_API_KEY=... FIREBASE_SERVICE_ACCOUNT=... node verify.js --uid <playerUid>
//   node verify.js --token <fcmToken>            # no Firebase credentials needed
//   node verify.js --uid <uid> --dry-run         # print the exact request only
//
// It uses the same helpers (lib.js) as the bridge, so a green run here means
// the workflow key, the FCM channel UUID and the Firebase credentials are all
// correct. Delivery to the device still shows up in Knock -> Workflows ->
// zs-push -> Runs.
//
// A real push goes out unless --dry-run is passed. --broadcast needs --yes.

"use strict";

const fs = require("fs");
const { buildTriggerData, recipientFor, deviceTokens, parseServiceAccount } = require("./lib");

const KNOCK_API_KEY = process.env.KNOCK_API_KEY;
const KNOCK_WORKFLOW_KEY = process.env.KNOCK_WORKFLOW_KEY || "zs-push";
const KNOCK_FCM_CHANNEL_ID =
  process.env.KNOCK_FCM_CHANNEL_ID || "c35285c4-1fb1-4b0c-b9ea-cb662dd7988b";
const KNOCK_API_BASE = (process.env.KNOCK_API_BASE || "https://api.knock.app").replace(/\/+$/, "");

function parseArgs(argv) {
  const args = { dryRun: false, broadcast: false, yes: false };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--dry-run") args.dryRun = true;
    else if (arg === "--broadcast") args.broadcast = true;
    else if (arg === "--yes") args.yes = true;
    else if (arg === "--uid") args.uid = argv[++i];
    else if (arg === "--token") args.token = argv[++i];
    else if (arg === "--title") args.title = argv[++i];
    else if (arg === "--message") args.message = argv[++i];
    else if (arg === "--help" || arg === "-h") args.help = true;
    else {
      console.error(`Unknown argument: ${arg}`);
      process.exit(2);
    }
  }
  return args;
}

const USAGE = `Usage: node verify.js [--uid <uid> | --token <fcmToken> | --broadcast --yes]
                       [--title "..." ] [--message "..."] [--dry-run]

  --uid        look the player's fcmToken up in Firestore (needs FIREBASE_SERVICE_ACCOUNT)
  --token      test one raw FCM token (no Firebase credentials needed)
  --broadcast  send to every registered device (requires --yes)
  --dry-run    print the request that would be sent, send nothing`;

async function collectRecipients(args, dryRun) {
  if (args.token) return [{ uid: args.uid || "verification", token: args.token }];
  if (args.dryRun && !args.uid) {
    // Payload-only dry run: no Firestore access needed.
    return [{ uid: "sample-uid", token: "sample-fcm-token" }];
  }

  const admin = require("firebase-admin");
  const sa = parseServiceAccount(process.env.FIREBASE_SERVICE_ACCOUNT, (file) =>
    fs.readFileSync(file, "utf8")
  );
  if (!sa && !process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    console.error(
      "FIREBASE_SERVICE_ACCOUNT (or GOOGLE_APPLICATION_CREDENTIALS) is required to look up tokens."
    );
    process.exit(1);
  }
  admin.initializeApp(sa ? { credential: admin.credential.cert(sa) } : undefined);
  const db = admin.firestore();

  if (args.broadcast) {
    const snapshot = await db.collection("players").get();
    return deviceTokens(snapshot.docs.map((doc) => ({ uid: doc.id, data: doc.data() })));
  }

  if (!args.uid) {
    console.error("Pass --uid <uid>, --token <fcmToken> or --broadcast --yes.");
    process.exit(2);
  }
  const snap = await db.collection("players").doc(args.uid).get();
  if (!snap.exists) {
    console.error(`No players/${args.uid} document.`);
    process.exit(1);
  }
  const devices = deviceTokens([{ uid: args.uid, data: snap.data() }]);
  if (devices.length === 0) {
    console.error(
      `players/${args.uid} has no fcmToken yet. Open the app on that device once (it saves the token on startup) and retry.`
    );
    process.exit(1);
  }
  return devices;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    console.log(USAGE);
    return;
  }
  if (args.broadcast && !args.yes) {
    console.error("--broadcast pushes to EVERY registered device. Re-run with --yes if that is intended.");
    process.exit(2);
  }
  if (!args.dryRun && !KNOCK_API_KEY) {
    console.error("KNOCK_API_KEY is not set.");
    process.exit(1);
  }

  const devices = await collectRecipients(args, args.dryRun);
  if (devices.length === 0) {
    console.error("No device tokens to target.");
    process.exit(1);
  }

  const docId = `verify-${Date.now()}`;
  const data = buildTriggerData(docId, {
    title: args.title || "ZERO STRESS test push",
    message: args.message || `Knock wiring check (${new Date().toISOString()})`,
    type: "admin",
  }, args.uid && !args.broadcast ? args.uid : null);
  const body = {
    recipients: devices.map((device) => recipientFor(KNOCK_FCM_CHANNEL_ID, device.uid, device.token)),
    data,
  };

  console.log(`Workflow : ${KNOCK_WORKFLOW_KEY} (${KNOCK_API_BASE})`);
  console.log(`Channel  : ${KNOCK_FCM_CHANNEL_ID}`);
  console.log(`Recipients: ${devices.length}${args.broadcast ? " (broadcast)" : ""}`);
  console.log(`Data     : ${JSON.stringify(data)}`);

  if (args.dryRun) {
    console.log("\n--dry-run: nothing sent. Full body:");
    console.log(JSON.stringify(body, null, 2));
    return;
  }

  const response = await fetch(
    `${KNOCK_API_BASE}/v1/workflows/${encodeURIComponent(KNOCK_WORKFLOW_KEY)}/trigger`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${KNOCK_API_KEY}`,
        "Content-Type": "application/json",
        "Idempotency-Key": docId,
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(15000),
    }
  );
  const text = await response.text();
  console.log(`\nHTTP ${response.status}: ${text.slice(0, 800)}`);
  if (!response.ok) {
    console.error("\nKnock rejected the trigger - see the troubleshooting table in server/README.md.");
    process.exit(1);
  }
  console.log(
    "\nAccepted. Open Knock -> Workflows -> " +
      KNOCK_WORKFLOW_KEY +
      " -> Runs: the FCM step should show as delivered. Check the device next."
  );
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
