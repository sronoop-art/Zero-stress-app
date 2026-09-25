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
//   ADMIN_EMAIL=you@x.com   make this account an admin (role=admin,
//                           status=approved). The doc is created if missing and
//                           PROMOTED if it already exists. Default: the oldest
//                           account, which is normally the owner.
//                           The value is matched the way the app signs in
//                           (LoginActivity.accountFor), so the PHONE NUMBER you
//                           use to log in works too: "1603242625" and
//                           "1603242625@zerostress.local" are the same account.

const admin = require("firebase-admin");

const DRY_RUN = (process.env.DRY_RUN || "false").toLowerCase() === "true";
const ADMIN_EMAIL = (process.env.ADMIN_EMAIL || "").toLowerCase();
// Resolved in main() once the account list is known; module-level so
// isAdminAccount() can read it.
let adminEmail = ADMIN_EMAIL;

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
    // NOTE: the paging cursor is `pageToken` - `page.token` is undefined and
    // threw "Cannot read properties of undefined (reading 'token')" on the
    // second iteration.
    page = await auth.listUsers(pageSize, page ? page.pageToken : undefined);
    users.push(...page.users);
  } while (page.pageToken);
  return users;
}

/**
 * Match the admin input the way the app resolves an account: the login screen
 * appends "@zerostress.local" to anything that is not already an email, so a
 * player types their PHONE number. Accept the number, the full email, or the
 * local part of a real email. Phone numbers are also compared digit-only, so
 * a stray leading zero (01603242625 vs 1603242625) still matches.
 */
function isAdminAccount(user) {
  if (!adminEmail) return false;
  const wanted = adminEmail.toLowerCase();
  const email = (user.email || "").toLowerCase();
  const local = email.split("@")[0];
  if (email === wanted || local === wanted || `${local}@zerostress.local` === wanted) {
    return true;
  }
  const digits = (s) => s.replace(/\D/g, "").replace(/^0+/, "");
  const wantedDigits = digits(wanted);
  return wantedDigits.length > 0 && digits(local) === wantedDigits;
}

async function main() {
  init();
  const auth = admin.auth();
  const db = admin.firestore();

  const users = await listAllUsers(auth);
  users.sort((a, b) => (a.metadata.creationTime || "").localeCompare(b.metadata.creationTime || ""));
  console.log(`Accounts: ${users.length}`);
  if (ADMIN_EMAIL) {
    const match = users.find(isAdminAccount);
    if (match) {
      adminEmail = (match.email || match.uid).toLowerCase();
      console.log(`Admin input "${ADMIN_EMAIL}" matches ${match.email || match.uid}`);
    } else {
      console.log(`WARNING: admin input "${ADMIN_EMAIL}" matches NO account.`);
      console.log(`Accounts available: ${users.map((u) => u.email || u.uid).join(", ") || "(none)"}`);
      console.log("Falling back to the oldest account so the backfill still works.");
      adminEmail = "";
    }
  }
  if (!adminEmail) {
    // No input: fall back to the oldest account, which is normally the owner.
    adminEmail = (users[0] && (users[0].email || users[0].uid) || "").toLowerCase();
    if (adminEmail) console.log(`No admin_email given - using the oldest account: ${adminEmail}`);
  }
  console.log(`Mode: ${DRY_RUN ? "DRY RUN (nothing written)" : "APPLY"}`);

  let created = 0;
  let skipped = 0;
  let promoted = 0;

  for (const user of users) {
    const ref = db.collection("players").doc(user.uid);
    const snap = await ref.get();
    const isAdmin = isAdminAccount(user);

    if (snap.exists) {
      skipped++;
      // Admin grant applies to an existing doc too - this is how an account
      // that registered normally gets promoted to admin by email.
      if (isAdmin) {
        const current = snap.data();
        const updates = {};
        if (current.role !== "admin") updates.role = "admin";
        if (current.status !== "approved") updates.status = "approved";
        if (Object.keys(updates).length > 0) {
          if (DRY_RUN) {
            console.log(`  [dry] would promote players/${user.uid} ${JSON.stringify(updates)}`);
          } else {
            await ref.update(updates);
            console.log(`  promoted players/${user.uid} -> admin (${JSON.stringify(updates)})`);
          }
          promoted++;
        } else {
          console.log(`  players/${user.uid} is already admin - nothing to do`);
        }
      }
      continue;
    }
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

  console.log(`Done. created=${created} alreadyPresent=${skipped} promoted=${promoted}`);
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
