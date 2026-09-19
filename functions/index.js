const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

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

    const title = notificationData.title || "ZERO STRESS";
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
    const db = getFirestore();
    const playersSnapshot = await db.collection("players").get();

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
