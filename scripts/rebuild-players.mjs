#!/usr/bin/env node
// Rebuild the Firestore `players` collection from the Firebase Authentication
// user list, after the collection was deleted by accident.
//
// Auth is a separate product from Firestore, so every account (uid, email,
// phone) is still there - only the Firestore documents are gone. This script
// reads the Auth list with a Google access token and recreates one
// `players/{uid}` document per account, using exactly the field set
// RegisterActivity writes, so the app's dashboards, leaderboards, chat and
// admin screens work again.
//
// It runs anywhere gcloud is installed and logged in - Google Cloud Shell in a
// phone browser is enough:
//
//   https://shell.cloud.google.com
//   git clone -b ZS3.1 https://github.com/sronoop-art/Zero-stress-app.git
//   cd Zero-stress-app
//   node scripts/rebuild-players.mjs                    # dry run, prints a plan
//   node scripts/rebuild-players.mjs --admin you@mail.com --apply
//
// Dry run is the default: nothing is written without --apply. Existing
// documents are never touched or overwritten.
//
// What this can and cannot bring back:
//   - players/ roster, roles, status, zeroed stats        -> recreated
//   - FCM tokens                                         -> app re-registers on next login
//   - seasons                                            -> the free cron job auto-creates one
//   - chat history, notifications, match logs, daily stats, titles, achievements
//     -> NOT recoverable; those documents are gone. Restore those only from a
//        `gcloud firestore export` backup, if one was ever taken.

import { execFileSync } from "node:child_process";
import https from "node:https";
import { URL, URLSearchParams } from "node:url";

const argv = process.argv.slice(2);
const flag = (name, fallback) => {
  const i = argv.indexOf(name);
  return i === -1 || i === argv.length - 1 ? fallback : argv[i + 1];
};

const PROJECT = flag("--project", "zerostress-3a536");
const ADMIN = flag("--admin", ""); // email or phone number of the admin account
const STATUS = flag("--status", "approved"); // "pending" to keep the approval gate
const APPLY = argv.includes("--apply");
const DATABASE = "(default)";

function accessToken() {
  try {
    return execFileSync("gcloud", ["auth", "print-access-token"], {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
  } catch {
    console.error("Could not get a Google access token (is `gcloud` installed and logged in?).");
    console.error("");
    console.error("Run this from Google Cloud Shell - https://shell.cloud.google.com -");
    console.error("or sign in first with:  gcloud auth login");
    process.exit(1);
  }
}

function request(method, url, body, token) {
  const target = new URL(url);
  const payload = body === undefined ? null : Buffer.from(JSON.stringify(body));
  return new Promise((resolve, reject) => {
    const req = https.request(
      {
        method,
        hostname: target.hostname,
        path: target.pathname + target.search,
        headers: {
          Authorization: `Bearer ${token}`,
          ...(payload
            ? { "Content-Type": "application/json", "Content-Length": payload.length }
            : {}),
        },
      },
      (res) => {
        const chunks = [];
        res.on("data", (c) => chunks.push(c));
        res.on("end", () => {
          const text = Buffer.concat(chunks).toString("utf8");
          let json = {};
          try {
            json = text ? JSON.parse(text) : {};
          } catch {
            json = { raw: text };
          }
          if (res.statusCode >= 200 && res.statusCode < 300) return resolve(json);
          reject(
            new Error(
              `${method} ${target.pathname} -> HTTP ${res.statusCode}: ${
                (json.error && json.error.message) || text.slice(0, 300)
              }`
            )
          );
        });
      }
    );
    req.on("error", reject);
    if (payload) req.write(payload);
    req.end();
  });
}

async function listAuthUsers(token) {
  const users = [];
  let pageToken = "";
  do {
    const body = { returnUserInfo: true, maxResults: 1000 };
    if (pageToken) body.nextPageToken = pageToken;
    const res = await request(
      "POST",
      `https://identitytoolkit.googleapis.com/v1/projects/${PROJECT}/accounts:query`,
      body,
      token
    );
    for (const user of res.userInfo || []) users.push(user);
    pageToken = res.nextPageToken || "";
  } while (pageToken);
  return users;
}

async function listPlayerIds(token) {
  const ids = new Set();
  let pageToken = "";
  do {
    const params = new URLSearchParams({
      pageSize: "1000",
      fields: "nextPageToken,documents(name)",
    });
    if (pageToken) params.set("pageToken", pageToken);
    const res = await request(
      "GET",
      `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/${DATABASE}/documents/players?${params}`,
      undefined,
      token
    );
    for (const doc of res.documents || []) ids.add(doc.name.split("/").pop());
    pageToken = res.nextPageToken || "";
  } while (pageToken);
  return ids;
}

function playerFields(user, isAdmin) {
  const uid = user.localId;
  // The app signs in with phone@zerostress.local, so phoneNumber is the name
  // people recognise; fall back to the email and finally to a short id.
  const name = user.displayName || user.phoneNumber || user.email || `player-${uid.slice(0, 6)}`;
  const str = (v) => ({ stringValue: String(v) });
  const num = (v) => ({ integerValue: String(v) });
  return {
    uid: str(uid),
    name: str(name),
    phone: str(user.phoneNumber || ""),
    role: str(isAdmin ? "admin" : "player"),
    status: str(isAdmin ? "approved" : STATUS),
    score: num(0),
    kills: num(0),
    deaths: num(0),
    assists: num(0),
    damage: num(0),
    wins: num(0),
    matches: num(0),
    xp: num(0),
    level: num(1),
    coins: num(0),
    rank: str("Iron"),
  };
}

async function main() {
  const token = accessToken();
  console.log(`Project: ${PROJECT}`);
  console.log(`Mode:    ${APPLY ? "APPLY (documents will be written)" : "dry run"}`);

  const [users, existing] = await Promise.all([
    listAuthUsers(token),
    listPlayerIds(token),
  ]);
  const missing = users.filter((u) => u.localId && !existing.has(u.localId));
  const isAdmin = (u) => Boolean(ADMIN) && (u.email === ADMIN || u.phoneNumber === ADMIN);

  console.log(`\nAuth accounts:      ${users.length}`);
  console.log(`players/ docs live: ${existing.size}`);
  console.log(`would create:       ${missing.length}\n`);

  for (const user of missing) {
    const fields = playerFields(user, isAdmin(user));
    console.log(
      `  players/${user.localId}  ${fields.name.stringValue}  role=${fields.role.stringValue} status=${fields.status.stringValue}`
    );
  }

  if (!ADMIN) {
    console.log("\nNo --admin given, so every rebuilt account gets role=player.");
    console.log("Re-run with your own login, e.g. --admin you@example.com --apply,");
    console.log("otherwise the admin screens stay locked (rules read players/{uid}.role).");
  }

  if (!APPLY) {
    console.log("\nDry run only - nothing was written. Add --apply to create these documents.");
    return;
  }

  let created = 0;
  const failed = [];
  for (const user of missing) {
    const fields = playerFields(user, isAdmin(user));
    try {
      await request(
        "POST",
        `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/${DATABASE}/documents/players?documentId=${encodeURIComponent(
          user.localId
        )}`,
        { fields },
        token
      );
      created++;
    } catch (err) {
      failed.push(`${user.localId}: ${err.message}`);
    }
  }

  console.log(`\nCreated: ${created}/${missing.length}`);
  for (const line of failed) console.log(`  failed ${line}`);
  console.log("\nNext:");
  console.log("  1. Open the app and log in - the FCM token is re-saved automatically.");
  console.log("  2. Check Admin Dashboard; your role comes from players/{uid}.role.");
  console.log("  3. Chat history and old stats are gone; only an export backup can bring them back.");
  console.log("  4. Names default to each account's phone/email - set real names from the app or console.");
}

main().catch((err) => {
  console.error(`\nFailed: ${err.message}`);
  process.exit(1);
});
