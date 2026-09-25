"use strict";

// Pure helpers for the Firestore -> Knock bridge. Kept free of side effects so
// they can be unit tested without Firebase or Knock credentials
// (see lib.test.js / `npm test`).

// Must match ZeroStressApp.CHANNEL_ID / CHAT_CHANNEL_ID / SCHEDULE_CHANNEL_ID
// (and functions/cron.js channelFor) so a backgrounded push lands on the same
// Android channel the in-app path would use.
function channelFor(type) {
  if (type === "chat" || type === "mention") return "zs_chat";
  if (type === "schedule") return "zs_schedule";
  return "zs_notifications";
}

/**
 * Build the Knock workflow `data` for one notification document.
 *
 * ZSFCMService.onMessageReceived reads exactly four data keys - title, body,
 * type and uid - so the keys Knock flattens into the FCM data payload have to
 * use those names ("message" is included as well for the workflow template and
 * for the Knock log).
 */
function buildTriggerData(docId, data, targetUid) {
  const type = data.type || "general";
  const text = data.message || data.body || "";
  const payload = {
    title: data.title || "ZERO STRESS",
    message: text,
    body: text,
    type,
    channelId: channelFor(type),
    notificationId: String(docId),
  };
  // Broadcasts must NOT carry a uid: the app drops any push whose uid is set
  // and different from the signed-in user, so a literal "all" would be thrown
  // away by every foreground client. Admin broadcasts (uid: null) and the
  // in-app-only match reminders rely on this.
  if (targetUid) payload.uid = targetUid;
  if (data.scheduleId) payload.scheduleId = String(data.scheduleId);
  return payload;
}

/** Knock recipient with the FCM token passed inline as channel data. */
function recipientFor(channelId, uid, token) {
  return {
    id: String(uid),
    channel_data: {
      [channelId]: { tokens: [token] },
    },
  };
}

/** Split a device list into Knock's 1000-recipient API batches. */
function chunk(items, size) {
  const out = [];
  for (let i = 0; i < items.length; i += size) out.push(items.slice(i, i + size));
  return out;
}

/** FCM data payloads must be strings; Knock stringifies trigger data for us. */
function stringifyValues(record) {
  const out = {};
  for (const key of Object.keys(record)) out[key] = String(record[key]);
  return out;
}

/**
 * Player docs -> { uid, token } for every device that registered a token.
 * Each entry is { uid, data } (see the Firestore snapshot mapping in index.js).
 */
function deviceTokens(players) {
  const devices = [];
  for (const player of players) {
    const token = player.data ? player.data.fcmToken : undefined;
    if (typeof token === "string" && token.length > 0) {
      devices.push({ uid: String(player.uid), token });
    }
  }
  return devices;
}

/**
 * Accept a Firebase service account as raw JSON, as base64 (hosts that mangle
 * newlines in `private_key` are the common case) or as a path to a JSON file.
 * Returns null when nothing was configured, so the caller can fall back to
 * Application Default Credentials.
 */
function parseServiceAccount(raw, readFile) {
  if (!raw) return null;
  const value = String(raw).trim();
  if (!value) return null;
  if (value.startsWith("{")) return JSON.parse(value);
  if (value.endsWith(".json")) {
    if (!readFile) throw new Error("parseServiceAccount: no readFile() provided for a file path");
    return JSON.parse(readFile(value));
  }
  const normalized = value
    .replace(/^data:[^,]+,/, "")
    .replace(/-/g, "+")
    .replace(/_/g, "/");
  return JSON.parse(Buffer.from(normalized, "base64").toString("utf8"));
}

/** One-line description of a service account for startup logs (never secret). */
function describeServiceAccount(sa) {
  if (!sa) return "application default credentials";
  return `${sa.project_id || "unknown-project"} (${sa.client_email || "unknown-client"})`;
}

module.exports = {
  channelFor,
  buildTriggerData,
  recipientFor,
  chunk,
  stringifyValues,
  deviceTokens,
  parseServiceAccount,
  describeServiceAccount,
};
