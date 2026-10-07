const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

/**
 * Real-time team-chat push.
 *
 * Fires the moment a new chat message lands in notifications/{notificationId}.
 * It reads the doc, finds every registered player device, and pushes a
 * high-importance zs_chat notification so every teammate's status bar gets it
 * in real time (Android 13+). Works for targeted uid docs as well as the
 * team broadcast (uid: null). Matches the existing event-triggered fan-out so
 * the free GitHub cron relay never doubles a send — the relay skips any doc
 * already carrying pushSent:true.
 */


/**
 * Push notification fan-out.
 *
 * Every notification document may carry a "uid" field identifying the single
 * user it belongs to:
 *   - uid present  -> push ONLY to that user's FCM token (targeted, no spam)
 *   - uid absent   -> broadcast to everyone (admin announcements etc.)
 *
 * The Android client also uses "uid" to filter notifications it receives,
 * so both layers agree.
 */
exports.sendPushNotification = onDocumentCreated(
  "notifications/{notificationId}",
  async (event) => {
    const notificationData = event.data ? event.data.data() : null;
    if (!notificationData) {
      console.log("No notification data found");
      return;
    }

    const title = notificationData.title || "ONLY TEAM-X";
    const body = notificationData.message || notificationData.body || "";
    const type = notificationData.type || "general";
    const senderId = notificationData.senderId || null;
    const targetUid = notificationData.uid || null;

    if (!body) {
      console.log("Notification body is empty, skipping");
      return;
    }

    // push:false = in-app only. The free-cron relay and this trigger both
    // honor it, so a uid-less reminder/broadcast doc can never become a
    // status-bar push to every player.
    if (notificationData.push === false) {
      console.log("push:false set - in-app notification only, skipping FCM");
      return;
    }

    console.log(
      `New notification: "${title}" type=${type} target=${targetUid || "broadcast"}`
    );

    // ------------------------------------------------------------------
    // Targeted push: only the owning user's device(s)
    // ------------------------------------------------------------------
    if (targetUid) {
      const db = getFirestore();
      const playerSnap = await db.collection("players").doc(targetUid).get();
      const token = playerSnap.exists ? playerSnap.data().fcmToken : null;

      if (!token || typeof token !== "string" || token.length === 0) {
        console.log(`No FCM token for user ${targetUid}, nothing to push`);
        return;
      }
      if (senderId && targetUid === senderId) {
        console.log("Skipping push to the sender themself");
        return;
      }

      const message = buildMessage(title, body, type, { tokens: [token] });
      try {
        const response = await getMessaging().sendEachForMulticast(message);
        console.log(
          `Targeted push to ${targetUid}: ${response.successCount} success, ${response.failureCount} failed`
        );
        if (response.failureCount > 0) {
          await clearInvalidTokens([{ id: targetUid, token }]);
        }
      } catch (error) {
        console.error("Targeted push failed:", error);
      }
      return;
    }

    // ------------------------------------------------------------------
    // Broadcast push: every registered device (topic first, token fallback)
    // ------------------------------------------------------------------
    const db2 = getFirestore();
    const playersSnapshot = await db2.collection("players").get();

    const tokens = [];
    for (const doc of playersSnapshot.docs) {
      if (senderId && doc.id === senderId) continue;
      const token = doc.data().fcmToken;
      if (token && typeof token === "string" && token.length > 0) {
        tokens.push(token);
      }
    }

    if (tokens.length === 0) {
      console.log("No FCM tokens found, attempting topic-based push...");
      try {
        const topicMessage = buildMessage(title, body, type, { topic: "all_players" });
        const topicResponse = await getMessaging().send(topicMessage);
        console.log("Topic push sent:", topicResponse);
      } catch (topicError) {
        console.error("Topic push failed:", topicError);
      }
      return;
    }

    console.log(`Sending broadcast push to ${tokens.length} devices`);

    // FCM multicast supports up to 500 tokens per call.
    let totalSuccess = 0;
    let totalFailure = 0;
    const failedTokens = [];
    for (let i = 0; i < tokens.length; i += 500) {
      const batch = tokens.slice(i, i + 500);
      const message = buildMessage(title, body, type, { tokens: batch });
      try {
        const response = await getMessaging().sendEachForMulticast(message);
        totalSuccess += response.successCount;
        totalFailure += response.failureCount;
        response.responses.forEach((resp, idx) => {
          if (!resp.success) failedTokens.push(batch[idx]);
        });
      } catch (error) {
        console.error("Broadcast batch failed:", error);
      }
    }
    console.log(`Push sent: ${totalSuccess} success, ${totalFailure} failed`);

    if (failedTokens.length > 0) {
      await clearInvalidTokens(
        playersSnapshot.docs
          .filter((doc) => failedTokens.includes(doc.data().fcmToken))
          .map((doc) => ({ id: doc.id, token: doc.data().fcmToken }))
      );
    }
  }
);

/**
 * Real-time team-chat push: fires the instant a chat/mention/reply message
 * lands in notifications/{notificationId}. It skips non-chat types,
 * skips the sender, and skips any doc already marked pushSent/pushDeduped, so
 * the free GitHub Actions cron relay never doubles a send.
 */
exports.sendTeamChatPush = onDocumentCreated(
  "notifications/{notificationId}",
  async (event) => {
    const notificationData = event.data ? event.data.data() : null;
    if (!notificationData) {
      console.log("No notification data found");
      return;
    }

    // Ignore duplicates already marked delivered by this trigger or the relay.
    if (notificationData.pushSent === true) return;
    if (notificationData.pushDeduped === true) return;

    const title = notificationData.title || "ONLY TEAM-X";
    const body = notificationData.message || notificationData.body || "";
    const type = notificationData.type || "chat";
    const senderId = notificationData.senderId || null;
    const targetUid = notificationData.uid || null;

    if (!body) {
      console.log("Team-chat body empty, skipping");
      return;
    }

    // In-app only docs never reach the status bar.
    if (notificationData.push === false) {
      console.log("push:false set - in-app only, skipping status-bar push");
      return;
    }

    // Only chat / mention / co-message / reply go to the team chat status bar.
    if (type !== "chat" && type !== "mention" && type !== "co_message" && type !== "reply") {
      console.log(`Team-chat trigger ignoring type=${type}`);
      return;
    }

    // Never push to the sender themselves.
    if (senderId && targetUid === senderId) {
      console.log("Skipping push to the sender themself");
      return;
    }

    console.log(
      `Team-chat push: "${title}" type=${type} target=${targetUid || "broadcast"} sender=${senderId || "unknown"}`
    );

    const db = getFirestore();
    const playersSnapshot = await db.collection("players").get();
    const tokens = [];

    // Targeted: push to only the owning user's device.
    if (targetUid) {
      const token = playersSnapshot.docs.find((doc) => doc.id === targetUid)
        ?.data().fcmToken;
      if (token && typeof token === "string" && token.length > 0) {
        if (senderId && targetUid === senderId) {
          console.log("Skipping push to the sender themself for targeted push");
          return;
        }
        tokens.push(token);
      } else {
        console.log(`No FCM token for player ${targetUid}, nothing to push`);
      }
      const message = buildMessage(title, body, type, { tokens });
      try {
        const response = await getMessaging().sendEachForMulticast(message);
        console.log(
          `Targeted push to ${targetUid}: ${response.successCount} success, ${response.failureCount} failed`
        );
        if (response.failureCount > 0) {
          await clearInvalidTokens(
            playersSnapshot.docs.filter((doc) => doc.id === targetUid)
              .map((doc) => ({ id: doc.id, token: doc.data().fcmToken }))
          );
        }
      } catch (error) {
        console.error("Targeted team-chat push failed:", error);
      }
      return;
    }

    // Broadcast: skip the sender's token for chat/mention/co_message/reply.
    for (const doc of playersSnapshot.docs) {
      const token = doc.data().fcmToken;
      if (token && typeof token === "string" && token.length > 0) {
        if (senderId && token === senderId) continue;
        tokens.push(token);
      }
    }

    if (tokens.length === 0) {
      console.log("No FCM tokens found, skipping team-chat push");
      return;
    }

    console.log(`Sending team-chat push to ${tokens.length} devices`);

    let totalSuccess = 0;
    let totalFailure = 0;
    const failedTokens = [];
    for (let i = 0; i < tokens.length; i += 500) {
      const batch = tokens.slice(i, i + 500);
      const message = buildMessage(title, body, type, { tokens: batch });
      try {
        const response = await getMessaging().sendEachForMulticast(message);
        totalSuccess += response.successCount;
        totalFailure += response.failureCount;
        response.responses.forEach((resp, idx) => {
          if (!resp.success) failedTokens.push(batch[idx]);
        });
      } catch (error) {
        console.error("Team-chat batch failed:", error);
      }
    }
    console.log(
      `Team-chat push sent: ${totalSuccess} success, ${totalFailure} failed`
    );

    if (failedTokens.length > 0) {
      await clearInvalidTokens(
        playersSnapshot.docs
          .filter((doc) => failedTokens.includes(doc.data().fcmToken))
          .map((doc) => ({ id: doc.id, token: doc.data().fcmToken }))
      );
    }
  }
);

/**
 * Trigger: When the admin adds a new match schedule, push an alert
 * to every registered player device.
 */
exports.sendScheduleNotification = onDocumentCreated(
  "match_schedules/{scheduleId}",
  async (event) => {
    const data = event.data ? event.data.data() : null;
    if (!data) return;

    const title = data.title || "New Match";
    const when = data.dateTime || "TBD";
    const type = data.type || "Custom";

    const db = getFirestore();
    const playersSnapshot = await db.collection("players").get();

    const tokens = [];
    for (const doc of playersSnapshot.docs) {
      const token = doc.data().fcmToken;
      if (token && typeof token === "string" && token.length > 0) {
        tokens.push(token);
      }
    }

    if (tokens.length === 0) {
      console.log("No FCM tokens found, attempting topic-based push for schedule...");
      try {
        const topicMessage = buildMessage(
          "Match Scheduled: " + title,
          type + " match - " + when + "\nOpen the app to view the schedule.",
          "schedule",
          { topic: "match_updates" }
        );
        const topicResponse = await getMessaging().send(topicMessage);
        console.log("Schedule topic push sent:", topicResponse);
      } catch (topicError) {
        console.error("Schedule topic push failed:", topicError);
      }
      return;
    }

    const message = buildMessage(
      "Match Scheduled: " + title,
      type + " match - " + when + "\nOpen the app to view the schedule.",
      "schedule",
      { tokens: tokens }
    );

    try {
      const response = await getMessaging().sendEachForMulticast(message);
      console.log(
        `Schedule push sent: ${response.successCount} success, ${response.failureCount} failed`
      );
    } catch (error) {
      console.error("Error sending schedule push:", error);
    }
  }
);

/**
 * Trigger: When a document is added to the "announcements" collection,
 * send a push notification for announcements.
 */
exports.sendAnnouncementNotification = onDocumentCreated(
  "announcements/{announcementId}",
  async (event) => {
    const data = event.data ? event.data.data() : null;
    if (!data) return;

    const text = data.text || "";
    if (!text) return;

    const db = getFirestore();
    const playersSnapshot = await db.collection("players").get();

    const tokens = [];
    for (const doc of playersSnapshot.docs) {
      const token = doc.data().fcmToken;
      if (token && typeof token === "string" && token.length > 0) {
        tokens.push(token);
      }
    }

    if (tokens.length === 0) {
      console.log("No FCM tokens found, attempting topic-based push for announcement...");
      try {
        const topicMessage = buildMessage("Announcement", text, "announcement", {
          topic: "announcements",
        });
        const topicResponse = await getMessaging().send(topicMessage);
        console.log("Announcement topic push sent:", topicResponse);
      } catch (topicError) {
        console.error("Announcement topic push failed:", topicError);
      }
      return;
    }

    const message = buildMessage("Announcement", text, "announcement", {
      tokens: tokens,
    });

    try {
      const response = await getMessaging().sendEachForMulticast(message);
      console.log(
        `Announcement push sent: ${response.successCount} success, ${response.failureCount} failed`
      );
    } catch (error) {
      console.error("Error sending announcement push:", error);
    }
  }
);

// ----------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------

function buildMessage(title, body, type, options) {
  const base = {
    notification: { title: title, body: body },
    data: {
      title: title,
      body: body,
      type: type,
    },
    android: {
      priority: "high",
      notification: {
        channelId:
          type === "chat" || type === "mention"
            ? "zs_chat"
            : type === "schedule"
            ? "zs_schedule"
            : "zs_notifications",
        priority: "high",
      },
    },
  };
  if (options.topic) return { ...base, topic: options.topic };
  return { ...base, tokens: options.tokens || [] };
}

async function clearInvalidTokens(entries) {
  if (entries.length === 0) return;
  const db = getFirestore();
  const batch = db.batch();
  for (const entry of entries) {
    batch.update(db.collection("players").doc(entry.id), { fcmToken: null });
  }
  try {
    await batch.commit();
    console.log(`Cleaned up ${entries.length} invalid tokens`);
  } catch (error) {
    console.error("Token cleanup failed:", error);
  }
}

/*
 * Scheduled functions (resetLeaderboards / autoSeasonReset) were REMOVED -
 * they are owned by the free GitHub Actions cron (functions/cron.js) so they
 * never run twice. See the note above matchReminders' former location.
 */

/**
 * Scheduled: match reminders. Every 5 minutes, find upcoming matches that
 * start within the next 15 minutes and push one reminder per match.
 * REMOVED: scheduled jobs live in functions/cron.js, run by the free
 * GitHub Actions cron (.github/workflows/free-cron.yml). Keeping them here
 * too would double-send reminders and double-reset leaderboards on projects
 * where Cloud Functions are deployed (Blaze). This file stays event-triggered
 * only; the GitHub cron owns all scheduled work (resets, seasons, reminders,
 * push relay).
 */
