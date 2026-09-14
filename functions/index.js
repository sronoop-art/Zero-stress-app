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

/**
 * Scheduled: daily leaderboard resets + season auto-rollover.
 * - Every day 00:00 UTC: zero dailyScore/dailyWins (and dailyKills).
 * - Mondays: zero weekly fields. 1st of month: zero monthly fields.
 * - Expired seasons are deactivated, top-3 players get their coins, and a
 *   fresh 30-day season is created automatically.
 */
const { onSchedule } = require("firebase-functions/v2/scheduler");
const DAY_MS = 24 * 60 * 60 * 1000;

async function resetFields(fieldScore, fieldWins, fieldKills) {
  const db = getFirestore();
  const snap = await db.collection("players")
    .where("status", "==", "approved").get();
  let batch = db.batch();
  let ops = 0;
  for (const doc of snap.docs) {
    const update = { [fieldScore]: 0, [fieldWins]: 0 };
    if (fieldKills) update[fieldKills] = 0;
    batch.update(doc.ref, update);
    ops++;
    if (ops === 400) {
      await batch.commit();
      batch = db.batch();
      ops = 0;
    }
  }
  if (ops > 0) await batch.commit();
}

exports.resetLeaderboards = onSchedule("every day 00:00", async (event) => {
  const now = new Date();
  const jobs = [resetFields("dailyScore", "dailyWins", "dailyKills")];
  if (now.getUTCDay() === 1) {
    jobs.push(resetFields("weeklyScore", "weeklyWins", "weeklyKills"));
  }
  if (now.getUTCDate() === 1) {
    jobs.push(resetFields("monthlyScore", "monthlyWins", "monthlyKills"));
  }
  await Promise.all(jobs);
  console.log("Leaderboard reset done");
});

exports.autoSeasonReset = onSchedule("every day 00:05", async (event) => {
  const db = getFirestore();
  const now = Date.now();

  // 1) Expire seasons older than their duration (days).
  const seasons = await db.collection("seasons")
    .where("active", "==", true).get();
  for (const season of seasons.docs) {
    const data = season.data();
    const created = data.createdAt || 0;
    const days = parseInt(data.duration || "30", 10) || 30;
    if (created > 0 && now - created > days * DAY_MS) {
      // Award top-3 approved players by all-time score.
      const top = await db.collection("players")
        .where("status", "==", "approved")
        .orderBy("score", "desc").limit(3).get();
      const rewards = [data.topRewardCoins || 500, 300, 150];
      let rank = 0;
      for (const p of top.docs) {
        const coins = (p.data().coins || 0) + (rewards[rank] || 0);
        await p.ref.update({ coins });
        await db.collection("notifications").add({
          uid: p.id,
          title: "Season ended - you placed #" + (rank + 1) + "!",
          message: `You earned ${rewards[rank] || 0} coins in ${data.name || "the season"}.`,
          type: "achievement",
          timestamp: now,
        });
        rank++;
      }
      await season.ref.update({ active: false, endedAt: now });
      console.log(`Season ${season.id} ended; top players rewarded`);
    }
  }

  // 2) Ensure an active season exists (auto-create 30-day seasons).
  const activeCount = await db.collection("seasons")
    .where("active", "==", true).count().get();
  if (activeCount.data().count === 0) {
    const total = await db.collection("seasons").count().get();
    await db.collection("seasons").add({
      name: "Season " + (total.data().count + 1),
      description: "Auto-created season",
      duration: "30",
      active: true,
      createdAt: now,
    });
    console.log("New season auto-created");
  }
});

/**
 * Scheduled: match reminders. Every 5 minutes, find upcoming matches that
 * start within the next 15 minutes and push one reminder per match.
 */
exports.matchReminders = onSchedule("every 5 minutes", async (event) => {
  const db = getFirestore();
  const now = Date.now();
  const windowEnd = now + 15 * 60 * 1000;

  const snap = await db.collection("match_schedules")
    .where("status", "==", "Upcoming")
    .where("matchTime", ">=", now - DAY_MS) // guard against clock skew
    .where("matchTime", "<=", windowEnd)
    .get();

  for (const doc of snap.docs) {
    if (doc.data().reminderSent) continue;
    const data = doc.data();
    await db.collection("notifications").add({
      title: "Match starting soon: " + (data.title || "Match"),
      message: `${data.type || "Match"} starts at ${data.dateTime || "soon"}. Get ready!`,
      type: "schedule",
      timestamp: now,
      scheduleId: doc.id,
    });
    await doc.ref.update({ reminderSent: true });
    console.log("Reminder sent for match", doc.id);
  }
});
