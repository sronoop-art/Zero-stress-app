// Backfill players/{uid} documents for accounts that have no player doc.
//
// WHY: the app saves its FCM token with a merge-set on players/{uid}. Firestore
// rules only let a client CREATE that doc as role=player/status=pending, so a
// merge-set on a missing doc is rejected. Without a doc there is no fcmToken,
// and the push relay (functions/cron.js) has nothing to send to - notifications
// silently never arrive on the device.
//
// The app creates its own doc at registration (RegisterActivity), so this is
// only needed for accounts that registered BEFORE the doc existed (e.g. after
// the collections were deleted). New signups fix themselves.
//
// Usage (GitHub Actions - see .github/workflows/backfill-players.yml):
//   FIREBASE_SERVICE_ACCOUNT='<service account JSON>' node scripts/backfill-players.cjs
// Optional:
//   DRY_RUN=true            list what would be created and exit
//   ADMIN_EMAIL=you@x.com   force this account to role=admin (default: the
//                           oldest account, which is normally the owner)

const admin = require("firebase-admin");

const DRY_RUN = (process.env.DRY_RUN || "false").toLowerCase() === "true";
const ADMIN_EMAIL = (process.env.ADMIN_EMAIL || "").toLowerCase();

function init() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) throw new Error("FIREBASE_SERVICE_ACCOUNT env var is not set");
  admin.initializeApp({ credential: admin.credential.cert(JSON.parse(raw)) });
}

function displayName(user) {
  if (user.displayName) return user.displayName;
  if (user.email) return user.email.split("@")[0];
  return "Player";
}

async function listAllUsers(auth, pageSize = 1000) {
  const users = [];
  let page;
  do {
    page = await auth.listUsers(pageSize, page.token);
    users.push(...page.users);
  } while (page.pageToken);
  return users;
}

async function main() {
  init();
  const auth = admin.auth();
  const db = admin.firestore();

  const users = await listAllUsers(auth);
  users.sort((a, b) => (a.metadata.creationTime || "").localeCompare(b.metadata.creationTime || ""));
  const adminEmail = ADMIN_EMAIL || (users[0] && users[0].email) || "";
  console.log(`Accounts: ${users.length}`);
  console.log(`Admin account: ${adminEmail || "(none)"}`);
  console.log(`Mode: ${DRY_RUN ? "DRY RUN (nothing written)" : "APPLY"}`);

  let created = 0;
  let skipped = 0;

  for (const user of users) {
    const ref = db.collection("players").doc(user.uid);
    const snap = await ref.get();
    if (snap.exists) {
      skipped++;
      continue;
    }
    const isAdmin = !!user.email && user.email.toLowerCase() === adminEmail;
    const data = {
      name: displayName(user),
      role: isAdmin ? "admin" : "player",
      status: "approved",
      backfilled: true,
      createdAt: Date.now(),
    };
    if (user.email) data.email = user.email;
    if (DRY_RUN) {
      console.log(`  [dry] would create players/${user.uid} ${JSON.stringify(data)}`);
    } else {
      await ref.set(data);
      console.log(`  created players/${user.uid} (${data.role})`);
    }
    created++;
  }

  console.log(`Done. created=${created} alreadyPresent=${skipped}`);
  if (!DRY_RUN) {
    console.log("Next: open the app once. The FCM token is written to the");
    console.log("player doc on login / chat send, then the next relay tick");
    console.log("(or the Knock bridge) delivers notifications.");
  }
}

main().then(
  () => process.exit(0),
  (err) => {
    console.error("Backfill failed:", err && err.message ? err.message : err);
    process.exit(1);
  },
);
