"use strict";

// Unit tests for the bridge's pure helpers. No Firebase or Knock credentials
// are needed: `node --test` (or `npm test`).
//
// The important assertions here are the ones that encode the Android
// contract - ZSFCMService.onMessageReceived reads the FCM data keys
// title / body / type / uid and DROPS any push whose uid differs from the
// signed-in user.

const test = require("node:test");
const assert = require("node:assert/strict");

const {
  channelFor,
  buildTriggerData,
  recipientFor,
  chunk,
  stringifyValues,
  deviceTokens,
  parseServiceAccount,
  describeServiceAccount,
} = require("./lib");

const CHANNEL_ID = "c35285c4-1fb1-4b0c-b9ea-cb662dd7988b";

test("channelFor maps types onto the app's notification channels", () => {
  assert.equal(channelFor("chat"), "zs_chat");
  assert.equal(channelFor("mention"), "zs_chat");
  assert.equal(channelFor("schedule"), "zs_schedule");
  assert.equal(channelFor("admin"), "zs_notifications");
  assert.equal(channelFor("achievement"), "zs_notifications");
  assert.equal(channelFor(undefined), "zs_notifications");
});

test("buildTriggerData carries the four keys the Android service reads", () => {
  const data = buildTriggerData("doc1", { title: "Hi", message: "Body", type: "admin" }, "user1");
  assert.equal(data.title, "Hi");
  assert.equal(data.body, "Body");
  assert.equal(data.message, "Body");
  assert.equal(data.type, "admin");
  assert.equal(data.uid, "user1");
  assert.equal(data.channelId, "zs_notifications");
  assert.equal(data.notificationId, "doc1");
});

test("buildTriggerData omits uid for broadcasts so foreground devices show them", () => {
  const data = buildTriggerData("doc2", { title: "All", message: "Hey", type: "admin" }, null);
  assert.equal("uid" in data, false);
  // and the fallback type is the generic channel
  assert.equal(data.channelId, "zs_notifications");
});

test("buildTriggerData stringifies scheduleId and defaults missing fields", () => {
  const data = buildTriggerData("doc3", { type: "schedule", scheduleId: 42 }, "user1");
  assert.equal(data.scheduleId, "42");
  assert.equal(data.title, "ZERO STRESS");
  assert.equal(data.body, "");
  assert.equal(data.channelId, "zs_schedule");
});

test("recipientFor passes the FCM token inline as channel data", () => {
  const recipient = recipientFor(CHANNEL_ID, "user1", "token-abc");
  assert.deepEqual(recipient, {
    id: "user1",
    channel_data: { [CHANNEL_ID]: { tokens: ["token-abc"] } },
  });
});

test("chunk splits into Knock's 1000-recipient batches", () => {
  const items = Array.from({ length: 2500 }, (_, i) => i);
  const batches = chunk(items, 1000);
  assert.equal(batches.length, 3);
  assert.equal(batches[0].length, 1000);
  assert.equal(batches[2].length, 500);
  assert.deepEqual(chunk([], 1000), []);
});

test("stringifyValues makes an FCM-safe data payload", () => {
  assert.deepEqual(stringifyValues({ type: "chat", scheduleId: 7 }), {
    type: "chat",
    scheduleId: "7",
  });
});

test("deviceTokens keeps only players with a registered token", () => {
  const devices = deviceTokens([
    { uid: "a", data: { fcmToken: "token-a" } },
    { uid: "b", data: { fcmToken: "" } },
    { uid: "c", data: {} },
    { uid: "d", data: { fcmToken: "token-d" } },
  ]);
  assert.deepEqual(devices, [
    { uid: "a", token: "token-a" },
    { uid: "d", token: "token-d" },
  ]);
});

test("parseServiceAccount accepts raw JSON, base64 and a file path", () => {
  const sa = { project_id: "zerostress-3a536", client_email: "x@y.iam.gserviceaccount.com" };
  assert.deepEqual(parseServiceAccount(JSON.stringify(sa)), sa);
  assert.deepEqual(parseServiceAccount(Buffer.from(JSON.stringify(sa)).toString("base64")), sa);
  assert.deepEqual(parseServiceAccount("   "), null);
  assert.deepEqual(parseServiceAccount(undefined), null);
  assert.deepEqual(parseServiceAccount("creds.json", () => JSON.stringify(sa)), sa);
});

test("describeServiceAccount never leaks key material", () => {
  const text = describeServiceAccount({ project_id: "p", client_email: "e", private_key: "SECRET" });
  assert.match(text, /p/);
  assert.equal(text.includes("SECRET"), false);
  assert.equal(describeServiceAccount(null), "application default credentials");
});
