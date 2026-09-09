const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

/**
 * Trigger: When a document is added to the "notifications" collection,
 * this function reads all player FCM tokens and sends a push notification.
 */
exports.sendPushNotification = onDocumentCreated(
  "notifications/{notificationId}",
  async (event) => {
    const notificationData = event.data.data();
    if (!notificationData) {
      console.log("No notification data found");
      return;
    }

    const title = notificationData.title || "ZERO STRESS";
    const body = notificationData.message || notificationData.body || "";
    const type = notificationData.type || "general";
    // Optional: skip pushing to the author of the notification (e.g. chat sender)
    const senderId = notificationData.senderId || null;

    if (!body) {
      console.log("Notification body is empty, skipping");
      return;
    }

    console.log(`New notification: "${title}" - "${body}"`);

    // Try topic-based push first (more reliable — sends to all subscribed devices)
    // Fall back to per-device token push if topic fails
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
      // Send to topic as fallback (players must be subscribed to all_players)
      try {
        const topicMessage = {
          notification: { title, body },
          data: { title, body, type },
          android: { priority: "high", notification: { channelId: "zs_notifications", priority: "high" } },
        };
        const topicResponse = await getMessaging().send({ ...topicMessage, topic: "all_players" });
        console.log("Topic push sent:", topicResponse);
      } catch (topicError) {
        console.error("Topic push failed:", topicError);
      }
      return;
    }

    console.log(`Sending push to ${tokens.length} devices`);

    // Choose channel based on type
    const channelId = type === "chat" || type === "mention" ? "zs_chat" : "zs_notifications";

    // Build FCM message
    const message = {
      notification: {
        title: title,
        body: body,
      },
      data: {
        title: title,
        body: body,
        type: type,
      },
      android: {
        priority: "high",
        notification: {
          channelId: channelId,
          priority: "high",
        },
      },
      tokens: tokens,
    };

    // Send in batches of 500 (FCM limit)
    try {
      const response = await getMessaging().sendEachForMulticast(message);
      console.log(`Push sent: ${response.successCount} success, ${response.failureCount} failed`);

      // Clean up invalid tokens
      if (response.failureCount > 0) {
        const failedTokens = [];
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            failedTokens.push(tokens[idx]);
          }
        });

        // Remove invalid tokens from Firestore
        const batch = db.batch();
        for (const snapshot of playersSnapshot.docs) {
          const playerToken = snapshot.data().fcmToken;
          if (failedTokens.includes(playerToken)) {
            batch.update(snapshot.ref, { fcmToken: null });
          }
        }
        await batch.commit();
        console.log(`Cleaned up ${failedTokens.length} invalid tokens`);
      }
    } catch (error) {
      console.error("Error sending push notification:", error);
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
    const data = event.data.data();
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
        const topicMessage = {
          notification: {
            title: "🗓️ Match Scheduled: " + title,
            body: type + " match • " + when + "\nOpen the app to view the schedule.",
          },
          data: {
            title: "Match Scheduled",
            body: title + " • " + when,
            type: "schedule",
          },
          android: { priority: "high", notification: { channelId: "zs_notifications", priority: "high" } },
        };
        const topicResponse = await getMessaging().send({ ...topicMessage, topic: "match_updates" });
        console.log("Schedule topic push sent:", topicResponse);
      } catch (topicError) {
        console.error("Schedule topic push failed:", topicError);
      }
      return;
    }

    const message = {
      notification: {
        title: "🗓️ Match Scheduled: " + title,
        body: type + " match • " + when + "\nOpen the app to view the schedule.",
      },
      data: {
        title: "Match Scheduled",
        body: title + " • " + when,
        type: "schedule",
      },
      android: {
        priority: "high",
        notification: {
          channelId: "zs_notifications",
          priority: "high",
        },
      },
      tokens: tokens,
    };

    try {
      const response = await getMessaging().sendEachForMulticast(message);
      console.log(`Schedule push sent: ${response.successCount} success, ${response.failureCount} failed`);
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
    const data = event.data.data();
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
    }    if (tokens.length === 0) {
      console.log("No FCM tokens found, attempting topic-based push for announcement...");
      try {
        const topicMessage = {
          notification: { title: "📢 Announcement", body: text },
          data: { title: "Announcement", body: text, type: "announcement" },
          android: { priority: "high", notification: { channelId: "zs_notifications", priority: "high" } },
        };
        const topicResponse = await getMessaging().send({ ...topicMessage, topic: "announcements" });
        console.log("Announcement topic push sent:", topicResponse);
      } catch (topicError) {
        console.error("Announcement topic push failed:", topicError);
      }
      return;
    }

    const message = {
      notification: {
        title: "📢 Announcement",
        body: text,
      },
      data: {
        title: "Announcement",
        body: text,
        type: "announcement",
      },
      android: {
        priority: "high",
        notification: {
          channelId: "zs_notifications",
          priority: "high",
        },
      },
      tokens: tokens,
    };

    try {
      const response = await getMessaging().sendEachForMulticast(message);
      console.log(`Announcement push sent: ${response.successCount} success, ${response.failureCount} failed`);
    } catch (error) {
      console.error("Error sending announcement push:", error);
    }
}
);
