/* ZERO STRESS - NEXUS web companion (PWA).
 *
 * Same Firebase project, same accounts, same Firestore shapes as the Android
 * app, so a player's profile, rank, history, DMs and coins are identical on
 * both platforms. Everything here mirrors the write shapes allowed by
 * firestore.rules - no rule changes were needed.
 */

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import {
  getAuth, onAuthStateChanged, signInWithEmailAndPassword,
  createUserWithEmailAndPassword, signOut
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";
import {
  getFirestore, doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, addDoc,
  collection, query, where, orderBy, limit, onSnapshot, runTransaction, writeBatch
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js";

/* ---------------- Firebase config (public client config, like google-services.json) ---------------- */
const firebaseConfig = {
  apiKey: "AIzaSyD0gGFGCRaZwXSFOuP4tMSA-qwQhHzDFoQ",
  authDomain: "zerostress-3a536.firebaseapp.com",
  projectId: "zerostress-3a536",
  storageBucket: "zerostress-3a536.appspot.com",
  messagingSenderId: "914592815895",
  appId: "1:914592815895:web:56c8edde35b879b0e4da30"
};

/* VAPID key for Web Push - paste the key pair generated in Firebase Console
 * > Project settings > Cloud Messaging > Web Push certificates into
 * Settings > Environment as ZS_WEB_PUSH_KEY (non-secret), or inline here.
 * Until set, the bell button explains itself instead of erroring. */
const VAPID_KEY = (typeof process !== "undefined" && process.env && process.env.ZS_WEB_PUSH_KEY) || "";
/* Fallback when the static host has no env injection (GitHub Pages etc.): */
let VAPID = VAPID_KEY || localStorage.getItem("zs_vapid") || "";

/* ---------------- rank ladder + score (port of ZsScore / Theme colors) ---------------- */
const RANKS = [
  { name: "Iron", min: 0, color: "#8B949E" },
  { name: "Bronze", min: 600, color: "#CD7F32" },
  { name: "Silver", min: 1200, color: "#C0C7D1" },
  { name: "Gold", min: 2000, color: "#FFC857" },
  { name: "Platinum", min: 3000, color: "#6EC1FF" },
  { name: "Diamond", min: 4000, color: "#20E7FF" },
  { name: "Heroic", min: 5000, color: "#31F7A5" },
  { name: "Master", min: 7000, color: "#8B5CFF" },
  { name: "Grandmaster", min: 10000, color: "#FF416C" }
];
function rankFor(score) {
  let r = RANKS[0];
  for (const t of RANKS) if (score >= t.min) r = t;
  return r;
}
function rankIndex(score) {
  let idx = 0;
  RANKS.forEach((t, i) => { if (score >= t.min) idx = i; });
  return idx;
}
const entryScore = (kills, damage, win) => kills * 10 + Math.floor(damage / 100) + (win ? 200 : 0);
const logScore = (d) => (typeof d.score === "number" && d.score) || entryScore(d.kills || 0, d.damage || 0, d.win === true);

/* ---------------- tiny helpers ---------------- */
const $ = (id) => document.getElementById(id);
const fmt = (n) => Number(n || 0).toLocaleString("en-US");
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const timeStr = (ms) => new Date(ms || Date.now()).toLocaleString(undefined, { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
function toast(msg) {
  let t = $("zs-toast");
  if (!t) { t = document.createElement("div"); t.id = "zs-toast"; document.body.appendChild(t); }
  t.textContent = msg;
  t.classList.add("show");
  clearTimeout(t._h);
  t._h = setTimeout(() => t.classList.remove("show"), 2600);
}
const LOCAL_DOMAIN = "@zerostress.local";
const normalizeId = (raw) => {
  const id = String(raw || "").trim();
  return id.includes("@") ? id : id + LOCAL_DOMAIN;
};
const displayNameOf = (id) => String(id || "").split("@")[0] || "Player";

/* ---------------- state ---------------- */
const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

let me = null;            // firebase user
let profile = null;       // players/{uid} doc
let playersCache = new Map(); // uid -> player doc (leaderboard + name lookups)
let myLogs = [];          // my match_logs
let notifDocs = [];       // my notifications
let lbScope = "lifetime";
let dmPeer = null;        // peer uid when thread open
let msgUnsub = null;      // dm messages listener
let firstOpen = { leaderboard: false, history: false, chat: false, dms: false, schedule: false, notifications: false, shop: false };
let pushedScreens = null;
let chatCount = 0;

/* =====================================================================
 * AUTH
 * ===================================================================== */
$("auth-toggle").onclick = () => {
  const reg = $("auth-go").dataset.mode === "register";
  $("auth-go").dataset.mode = reg ? "signin" : "register";
  $("auth-go").textContent = reg ? "SIGN IN" : "CREATE ACCOUNT";
  $("auth-toggle").textContent = reg ? "New player? Create account" : "Have an account? Sign in";
  $("auth-msg").textContent = "";
};

$("auth-go").onclick = async () => {
  const btn = $("auth-go");
  const msg = $("auth-msg");
  msg.textContent = "";
  const id = normalizeId($("auth-id").value);
  const pass = $("auth-pass").value;
  if (!id || id === LOCAL_DOMAIN || pass.length < 6) {
    msg.textContent = "Enter your player ID and a password (6+ characters).";
    return;
  }
  btn.disabled = true;
  const register = btn.dataset.mode === "register";
  try {
    if (register) {
      const cred = await createUserWithEmailAndPassword(auth, id, pass);
      await createPlayerDoc(cred.user.uid, id);
      toast("Account created - welcome!");
    } else {
      await signInWithEmailAndPassword(auth, id, pass);
    }
  } catch (e) {
    msg.textContent = authErrorText(e);
  } finally {
    btn.disabled = false;
  }
};

function authErrorText(e) {
  const code = e && e.code ? e.code : "";
  if (code.includes("invalid-credential") || code.includes("wrong-password") || code.includes("user-not-found")) return "Wrong ID or password.";
  if (code.includes("email-already-in-use")) return "That ID is already registered - sign in instead.";
  if (code.includes("weak-password")) return "Password must be at least 6 characters.";
  if (code.includes("invalid-email")) return "That ID is not valid.";
  if (code.includes("too-many-requests")) return "Too many attempts - try again in a minute.";
  if (code.includes("network")) return "Network error - check your connection.";
  return e && e.message ? e.message.replace("Firebase: ", "").slice(0, 120) : "Sign-in failed.";
}

/* Same 17-field profile doc as RegisterActivity writes. */
async function createPlayerDoc(uid, account) {
  await setDoc(doc(db, "players", uid), {
    uid,
    name: displayNameOf(account),
    phone: account,
    role: "player",
    status: "pending",
    score: 0, kills: 0, deaths: 0, assists: 0, damage: 0,
    wins: 0, matches: 0, xp: 0, level: 1,
    coins: 0,
    rank: "Iron"
  });
}

$("logout").onclick = async () => {
  try { await signOut(auth); location.reload(); } catch (e) { toast("Sign out failed"); }
};

onAuthStateChanged(auth, async (user) => {
  $("auth-view").classList.add("hidden");
  if (!user) {
    $("auth-view").classList.remove("hidden");
    me = null; profile = null;
    return;
  }
  me = user;
  $("app-view").classList.remove("hidden");
  boot();
});

/* =====================================================================
 * BOOT / LIVE LISTENERS
 * ===================================================================== */
function boot() {
  const uid = me.uid;
  const profRef = doc(db, "players", uid);

  // Profile: single source for dashboard/shop/profile screens.
  onSnapshot(profRef, (snap) => {
    profile = snap.exists() ? Object.assign({ id: snap.id }, snap.data()) : null;
    if (!profile) {
      // First web login for an account created in the app - make the doc.
      createPlayerDoc(uid, me.email || uid).catch(() => {});
      profile = { id: uid, name: displayNameOf(me.email || uid), score: 0, coins: 0 };
    }
    renderDashboard();
    renderShopCoins();
    renderProfile();
    maybeAutoSubscribePush();
  }, (err) => console.warn("profile listener:", err.message));

  // Unread alert badge.
  onSnapshot(
    query(collection(db, "notifications"), where("uid", "==", uid), orderBy("timestamp", "desc"), limit(50)),
    (snap) => {
      notifDocs = snap.docs.map((d) => Object.assign({ docId: d.id, ref: d.ref }, d.data()));
      const unread = notifDocs.filter((n) => !n.read && !n.deleted).length;
      const badge = $("notif-badge");
      if (unread > 0) { badge.textContent = unread > 9 ? "9+" : unread; badge.style.display = "grid"; }
      else badge.style.display = "none";
      if (firstOpen.notifications) renderNotifications();
    },
    (err) => console.warn("notifications listener:", err.message)
  );

  // Online status ping (OnOnlineStatusHelper equivalent).
  const ping = () => { if (document.visibilityState !== "hidden") updateDoc(profRef, { lastOnline: Date.now() }).catch(() => {}); };
  ping();
  setInterval(ping, 60 * 1000);
  document.addEventListener("visibilitychange", ping);

  initPush();

  // Deep link from a notification tap: /?screen=dms&thread=<threadId>
  pushedScreens = new URLSearchParams(location.search);
}

/* =====================================================================
 * NAV
 * ===================================================================== */
const TITLES = {
  dashboard: "DASHBOARD", leaderboard: "LEADERBOARD", history: "MATCH HISTORY",
  chat: "TEAM CHAT", dms: "DIRECT MESSAGES", schedule: "SCHEDULE",
  notifications: "ALERTS", shop: "COIN SHOP", profile: "PROFILE"
};
document.querySelectorAll(".nav-btn").forEach((btn) => {
  btn.onclick = () => show(btn.dataset.scr);
});
function show(name) {
  document.querySelectorAll(".screen").forEach((s) => s.classList.add("hidden"));
  const el = $("scr-" + name);
  if (el) el.classList.remove("hidden");
  $("screen-title").textContent = TITLES[name] || "ZERO STRESS";
  document.querySelectorAll(".nav-btn").forEach((b) => b.classList.toggle("active", b.dataset.scr === name));
  if (!firstOpen[name]) {
    firstOpen[name] = true;
    if (name === "leaderboard") loadLeaderboard();
    if (name === "history") loadHistory();
    if (name === "chat") openChat();
    if (name === "dms") openDmList();
    if (name === "schedule") loadSchedule();
    if (name === "shop") loadShop();
  } else {
    if (name === "leaderboard") renderLeaderboard();
    if (name === "history") renderHistory();
  }
}

/* =====================================================================
 * DASHBOARD
 * ===================================================================== */
function renderDashboard() {
  if (!profile) return;
  const score = Number(profile.score || 0);
  const r = rankFor(score);
  $("dash-name").textContent = profile.name || displayNameOf(me.email || me.uid);
  $("dash-role").textContent = (profile.gameRole || profile.region || "PLAYER").toUpperCase();
  $("dash-rank").textContent = r.name.toUpperCase();
  $("dash-rank-init").textContent = r.name.charAt(0);
  $("dash-rankhex").style.background = `linear-gradient(160deg, ${r.color}66, ${r.color}22)`;
  $("dash-rankhex").style.color = r.color;
  $("dash-pts").textContent = fmt(score) + " PTS";
  $("st-kills").textContent = fmt(profile.kills);
  $("st-deaths").textContent = fmt(profile.deaths);
  $("st-assists").textContent = fmt(profile.assists);
  $("st-wins").textContent = fmt(profile.wins);
  $("dash-coins").textContent = fmt(profile.coins);
  const idx = rankIndex(score);
  const cur = RANKS[idx];
  const next = RANKS[idx + 1];
  if (next) {
    $("bar-next").textContent = next.name.toUpperCase();
    $("bar-cur").textContent = fmt(score);
    $("bar-goal").textContent = fmt(next.min);
    const pct = Math.min(100, Math.round(((score - cur.min) / (next.min - cur.min)) * 100));
    $("bar-fill").style.width = pct + "%";
  } else {
    $("bar-next").textContent = "MAX RANK";
    $("bar-cur").textContent = fmt(score);
    $("bar-goal").textContent = "∞";
    $("bar-fill").style.width = "100%";
  }
}

/* =====================================================================
 * LEADERBOARD
 * ===================================================================== */
document.querySelectorAll("#lb-seg .seg-btn").forEach((b) => {
  b.onclick = () => {
    lbScope = b.dataset.scope;
    document.querySelectorAll("#lb-seg .seg-btn").forEach((x) => x.classList.toggle("active", x === b));
    renderLeaderboard();
  };
});
async function loadLeaderboard() {
  const list = $("lb-list");
  list.innerHTML = '<p class="empty">Loading ranks&hellip;</p>';
  try {
    // Single-field index only: no status filter, so the existing composite stays unused.
    const snap = await getDocs(query(collection(db, "players"), orderBy("score", "desc"), limit(100)));
    playersCache = new Map();
    snap.forEach((d) => playersCache.set(d.id, Object.assign({ id: d.id }, d.data())));
    renderLeaderboard();
  } catch (e) {
    list.innerHTML = '<p class="empty">Could not load leaderboard.</p>';
  }
}
function renderLeaderboard() {
  const field = { lifetime: "score", daily: "dailyScore", weekly: "weeklyScore", monthly: "monthlyScore" }[lbScope];
  const rows = [...playersCache.values()]
    .map((p) => ({ p, v: Number(p[field] || 0) }))
    .filter((x) => x.v > 0)
    .sort((a, b) => b.v - a.v)
    .slice(0, 100);
  const list = $("lb-list");
  if (!rows.length) { list.innerHTML = '<p class="empty">No ranked players yet.</p>'; return; }
  const medals = ["🥇", "🥈", "🥉"];
  list.innerHTML = rows.map((x, i) => {
    const r = rankFor(Number(x.p.score || 0));
    const mine = x.p.id === me.uid;
    return `<div class="glass row-item ${mine ? "mine-row" : ""}">
      <div class="row-rank">${medals[i] || i + 1}</div>
      <div class="row-main">
        <div class="row-title" style="color:${r.color}">${esc(x.p.name || displayNameOf(x.p.phone || x.p.id))}</div>
        <div class="row-sub">${r.name.toUpperCase()} &middot; ${esc(x.p.gameRole || "")}</div>
      </div>
      <div class="row-end"><b>${fmt(x.v)}</b><span>${lbScope.toUpperCase()}</span></div>
    </div>`;
  }).join("");
}

/* =====================================================================
 * MATCH HISTORY
 * ===================================================================== */
async function loadHistory() {
  $("mh-list").innerHTML = '<p class="empty">Loading matches&hellip;</p>';
  try {
    // (playerId, date) composite index - already deployed via firestore.indexes.json.
    const snap = await getDocs(query(
      collection(db, "match_logs"),
      where("playerId", "==", me.uid),
      orderBy("date", "desc"), limit(100)
    ));
    myLogs = snap.docs.map((d) => Object.assign({ id: d.id }, d.data()));
    renderHistory();
  } catch (e) {
    $("mh-list").innerHTML = '<p class="empty">Could not load match history.</p>';
  }
}
function renderHistory() {
  const list = $("mh-list");
  if (!myLogs.length) { list.innerHTML = '<p class="empty">No matches logged yet - admin results appear here.</p>'; return; }
  list.innerHTML = myLogs.map((d) => {
    const win = d.win === true;
    const kd = `${d.kills || 0} / ${d.deaths || 0} / ${d.assists || 0}`;
    return `<div class="glass row-item ${win ? "win-mark" : "loss-mark"}">
      <div class="row-main">
        <div class="row-title">${win ? "VICTORY" : "DEFEAT"} <span class="row-type">${esc((d.matchType || "CASUAL").toUpperCase())}</span></div>
        <div class="row-sub">${kd} &middot; ${fmt(d.damage || 0)} dmg &middot; ${timeStr(d.date)}</div>
      </div>
      <div class="row-end"><b>${fmt(logScore(d))}</b><span>SCORE</span></div>
    </div>`;
  }).join("");
}

/* =====================================================================
 * TEAM CHAT
 * ===================================================================== */
function openChat() {
  const scroller = $("chat-scroll");
  // Newest-first query (single-field index) rendered reversed.
  onSnapshot(
    query(collection(db, "chat_messages"), orderBy("timestamp", "desc"), limit(60)),
    (snap) => {
      const msgs = snap.docs.map((d) => d.data()).reverse();
      scroller.innerHTML = msgs.map((m) => chatBubble(m, m.senderId === me.uid)).join("") ||
        '<p class="empty">No messages yet - say gg.</p>';
      scroller.scrollTop = scroller.scrollHeight;
    },
    (err) => { scroller.innerHTML = '<p class="empty">Chat unavailable.</p>'; }
  );
}
function chatBubble(m, mine) {
  const who = m.senderId === me.uid ? "" :
    `<span class="who">${esc(displayNameOf(m.senderEmail) || playerName(m.senderId))}</span>`;
  return `<div class="bub ${mine ? "mine" : "theirs"}">${who}${esc(m.text || "")}<span class="ts">${timeStr(m.timestamp)}</span></div>`;
}
function playerName(uid) {
  const p = playersCache.get(uid);
  return p ? (p.name || displayNameOf(p.phone || uid)) : "Player";
}
$("chat-form").onsubmit = async (ev) => {
  ev.preventDefault();
  const input = $("chat-input");
  const text = input.value.trim();
  if (!text || !me) return;
  input.value = "";
  try {
    // senderId must equal auth.uid (firestore.rules chat_messages).
    await addDoc(collection(db, "chat_messages"), {
      senderId: me.uid, text, timestamp: Date.now()
    });
  } catch (e) {
    toast("Message failed: " + (e.code || e.message).slice(0, 60));
    input.value = text;
  }
};

/* =====================================================================
 * DIRECT MESSAGES
 * ===================================================================== */
let dmThreads = [];
function threadIdFor(a, b) { return a <= b ? a + "_" + b : b + "_" + a; }

function openDmList() {
  // No composite index needed: array-contains only, sorted client-side.
  onSnapshot(
    query(collection(db, "dm_threads"), where("participants", "array-contains", me.uid)),
    (snap) => {
      dmThreads = snap.docs.map((d) => Object.assign({ id: d.id }, d.data()))
        .sort((a, b) => (b.lastAt || 0) - (a.lastAt || 0));
      renderDmList();
    },
    (err) => { $("dm-list").innerHTML = '<p class="empty">Messages unavailable.</p>'; }
  );
}
function renderDmList() {
  const list = $("dm-list");
  if (!dmThreads.length) {
    list.innerHTML = '<p class="empty">No conversations yet - find a player below.</p>';
  } else {
    list.innerHTML = dmThreads.map((t) => {
      const peerUid = (t.participants || []).find((p) => p !== me.uid) || "";
      const nm = (t.names && t.names[peerUid]) || playerName(peerUid);
      return `<div class="glass row-item" data-peer="${esc(peerUid)}">
        <div class="row-main">
          <div class="row-title">${esc(nm)}</div>
          <div class="row-sub">${esc(t.lastMessage || "New conversation")}</div>
        </div>
        <div class="row-end"><span>${t.lastAt ? timeStr(t.lastAt).split(",")[0] : ""}</span></div>
      </div>`;
    }).join("");
    list.querySelectorAll("[data-peer]").forEach((el) => { el.onclick = () => openThread(el.dataset.peer); });
  }
  // Deep-link ?thread=<threadId>
  if (pushedScreens && pushedScreens.get("thread")) {
    const tid = pushedScreens.get("thread");
    pushedScreens.delete("thread");
    const t = dmThreads.find((x) => x.id === tid);
    if (t) {
      const peer = (t.participants || []).find((p) => p !== me.uid);
      if (peer) openThread(peer);
    }
  }
}

$("dm-new").onsubmit = async (ev) => {
  ev.preventDefault();
  const q = $("dm-find").value.trim().toLowerCase();
  const res = $("dm-results");
  if (!q) { res.innerHTML = ""; return; }
  if (!playersCache.size) {
    const snap = await getDocs(query(collection(db, "players"), orderBy("score", "desc"), limit(200)));
    playersCache = new Map();
    snap.forEach((d) => playersCache.set(d.id, Object.assign({ id: d.id }, d.data())));
  }
  const hits = [...playersCache.values()]
    .filter((p) => p.id !== me.uid)
    .filter((p) => {
      const nm = (p.name || "").toLowerCase();
      const ph = (p.phone || "").toLowerCase();
      return nm.includes(q) || ph.includes(q) || p.id.toLowerCase() === q;
    })
    .slice(0, 10);
  res.innerHTML = hits.length
    ? hits.map((p) => `<div class="glass row-item" data-peer="${esc(p.id)}">
        <div class="row-main"><div class="row-title">${esc(p.name || displayNameOf(p.phone || p.id))}</div>
        <div class="row-sub">${rankFor(Number(p.score || 0)).name.toUpperCase()}</div></div>
        <div class="row-end"><span>MESSAGE →</span></div></div>`).join("")
    : '<p class="empty">No player matched.</p>';
  res.querySelectorAll("[data-peer]").forEach((el) => { el.onclick = () => openThread(el.dataset.peer); });
};

$("dm-back").onclick = () => {
  if (msgUnsub) { msgUnsub(); msgUnsub = null; }
  dmPeer = null;
  $("dm-thread").classList.add("hidden");
  $("dm-list").classList.remove("hidden");
  $("dm-new").classList.remove("hidden");
  $("dm-results").classList.remove("hidden");
};

async function openThread(peerUid) {
  if (msgUnsub) { msgUnsub(); msgUnsub = null; }
  dmPeer = peerUid;
  // Peer name: cache, or one fetch.
  let peerName = playerName(peerUid);
  if (!playersCache.has(peerUid)) {
    const ps = await getDoc(doc(db, "players", peerUid));
    if (ps.exists()) { playersCache.set(peerUid, Object.assign({ id: ps.id }, ps.data())); peerName = ps.data().name || displayNameOf(peerUid); }
  }
  $("dm-peer-name").textContent = peerName;
  $("dm-list").classList.add("hidden");
  $("dm-new").classList.add("hidden");
  $("dm-results").classList.add("hidden");
  $("dm-thread").classList.remove("hidden");

  const tRef = doc(db, "dm_threads", threadIdFor(me.uid, peerUid));
  // Same metadata upsert as DirectMessageActivity (participants must stay identical).
  try {
    await setDoc(tRef, {
      participants: [me.uid, peerUid].sort(),
      names: { [me.uid]: (profile && profile.name) || displayNameOf(me.email) }
    }, { merge: true });
  } catch (e) { console.warn("thread upsert:", e.message); }

  msgUnsub = onSnapshot(
    query(collection(tRef, "messages"), orderBy("timestamp", "asc"), limit(200)),
    (snap) => {
      const msgs = snap.docs.map((d) => d.data());
      $("dm-scroll").innerHTML = msgs.map((m) => chatBubble(m, m.senderId === me.uid)).join("") ||
        '<p class="empty">Say hi 👋</p>';
      $("dm-scroll").scrollTop = $("dm-scroll").scrollHeight;
    },
    (err) => { $("dm-scroll").innerHTML = '<p class="empty">Cannot open this conversation.</p>'; }
  );
}

$("dm-form").onsubmit = async (ev) => {
  ev.preventDefault();
  const input = $("dm-input");
  const text = input.value.trim();
  if (!text || !dmPeer || !me) return;
  input.value = "";
  const threadId = threadIdFor(me.uid, dmPeer);
  const tRef = doc(db, "dm_threads", threadId);
  const now = Date.now();
  try {
    // Same atomic batch as the Android app: thread meta + message + push doc.
    const batch = writeBatch(db);
    batch.set(tRef, {
      participants: [me.uid, dmPeer].sort(),
      names: { [me.uid]: (profile && profile.name) || displayNameOf(me.email) },
      lastMessage: text, lastSenderId: me.uid, lastAt: now
    }, { merge: true });
    batch.set(doc(collection(tRef, "messages")), { senderId: me.uid, text, timestamp: now });
    batch.set(doc(collection(db, "notifications")), {
      uid: dmPeer, title: "New direct message", message: text,
      type: "dm", timestamp: now, senderId: me.uid, threadId
    });
    await batch.commit();
  } catch (e) {
    toast("Send failed: " + (e.code || e.message).slice(0, 60));
    input.value = text;
  }
};

/* =====================================================================
 * SCHEDULE
 * ===================================================================== */
async function loadSchedule() {
  $("sch-list").innerHTML = '<p class="empty">Loading schedule&hellip;</p>';
  try {
    const snap = await getDocs(query(collection(db, "match_schedules"), orderBy("matchTime", "desc"), limit(50)));
    const rows = snap.docs.map((d) => Object.assign({ id: d.id }, d.data()));
    $("sch-list").innerHTML = rows.length ? rows.map((d) => `
      <div class="glass row-item">
        <div class="row-main">
          <div class="row-title">${esc(d.title || "Match")}</div>
          <div class="sched-line"><span>${esc((d.type || "MATCH").toUpperCase())}</span><span>${esc(d.status || "")}</span></div>
          <div class="row-sub">${esc(d.dateTime || timeStr(d.matchTime))}</div>
        </div>
      </div>`).join("") : '<p class="empty">No matches scheduled.</p>';
  } catch (e) {
    $("sch-list").innerHTML = '<p class="empty">Schedule unavailable.</p>';
  }
}

/* =====================================================================
 * NOTIFICATIONS
 * ===================================================================== */
function renderNotifications() {
  const list = $("notif-list");
  if (!notifDocs.length) { list.innerHTML = '<p class="empty">No alerts yet.</p>'; return; }
  list.innerHTML = notifDocs.map((n) => `
    <div class="glass row-item ${n.read ? "" : "unread-row"}">
      <div class="row-main">
        <div class="row-title">${esc(n.title || "ZERO STRESS")}</div>
        <div class="row-sub">${esc(n.message || "")}</div>
        <div class="row-sub" style="color:var(--text-3)">${timeStr(n.timestamp)}</div>
      </div>
      ${n.uid ? `<button class="notif-x" data-del="${esc(n.docId)}" aria-label="Delete">&times;</button>` : ""}
    </div>`).join("");
  list.querySelectorAll("[data-del]").forEach((b) => {
    b.onclick = async () => {
      const n = notifDocs.find((x) => x.docId === b.dataset.del);
      if (n) { try { await deleteDoc(n.ref); } catch (e) { toast("Cannot delete"); } }
    };
  });
}
$("notif-clear").onclick = async () => {
  const mine = notifDocs.filter((n) => n.uid);
  if (!mine.length) { toast("Nothing to clear"); return; }
  for (const n of mine) { try { await deleteDoc(n.ref); } catch (e) {} }
};
// Mark my unread docs as read whenever the alerts screen opens.
$("nav-notif").addEventListener("click", () => {
  setTimeout(() => {
    notifDocs.filter((n) => n.uid && !n.read).slice(0, 20).forEach((n) => {
      updateDoc(n.ref, { read: true }).catch(() => {});
    });
  }, 600);
});

/* =====================================================================
 * SHOP
 * ===================================================================== */
let shopItems = [];
let myTitles = new Set();
function renderShopCoins() {
  $("shop-coins").textContent = fmt(profile && profile.coins);
}
async function loadShop() {
  $("shop-grid").innerHTML = '<p class="empty">Loading shop&hellip;</p>';
  try {
    const [items, titles] = await Promise.all([
      getDocs(collection(db, "shop_items")),
      getDocs(query(collection(db, "player_titles"), where("uid", "==", me.uid)))
    ]);
    shopItems = items.docs.map((d) => Object.assign({ id: d.id }, d.data())).filter((i) => i.hidden !== true);
    myTitles = new Set(titles.docs.map((d) => Object.assign({ id: d.id }, d.data()))
      .map((t) => t.itemID || t.itemId || (t.id || "").split("_")[0]));
    renderShop();
  } catch (e) {
    $("shop-grid").innerHTML = '<p class="empty">Shop unavailable.</p>';
  }
}
function renderShop() {
  const grid = $("shop-grid");
  if (!shopItems.length) { grid.innerHTML = '<p class="empty">Shop is being stocked - ask an admin.</p>'; return; }
  grid.innerHTML = shopItems.map((it) => {
    const owned = myTitles.has(it.id);
    const icon = it.iconUrl
      ? `<img src="${esc(it.iconUrl)}" alt="" loading="lazy">`
      : esc((it.title || it.name || "?").charAt(0).toUpperCase());
    return `<div class="glass shop-item ${owned ? "owned" : ""}">
      <div class="icon">${icon}</div>
      <div class="nm">${esc(it.title || it.name || "Item")}</div>
      <div class="ds">${esc(it.description || "")}</div>
      <div class="cost">${fmt(it.price)} 🪙</div>
      ${owned ? '<div class="badge-owned">OWNED</div>'
              : `<button class="btn-primary" data-buy="${esc(it.id)}">BUY</button>`}
    </div>`;
  }).join("");
  grid.querySelectorAll("[data-buy]").forEach((b) => { b.onclick = () => buyItem(b.dataset.buy, b); });
}
async function buyItem(itemId, btn) {
  if (!profile) return;
  btn.disabled = true; btn.textContent = "…";
  const itemRef = doc(db, "shop_items", itemId);
  const profRef = doc(db, "players", me.uid);
  try {
    // Same transactional purchase as ShopActivity: re-read the LIVE item doc
    // (admin may have changed price/hidden it) and the live balance.
    await runTransaction(db, async (tx) => {
      const itSnap = await tx.get(itemRef);
      if (!itSnap.exists()) throw new Error("Item no longer exists");
      const it = itSnap.data();
      if (it.hidden === true) throw new Error("Item was just hidden");
      const price = Number(it.price || 0);
      const pSnap = await tx.get(profRef);
      const balance = Number((pSnap.data() || {}).coins || 0);
      if (balance < price) throw new Error("Not enough coins");
      tx.update(profRef, { coins: balance - price });
      tx.set(doc(collection(db, "player_titles")), {
        uid: me.uid, itemID: itemId, title: it.title || "Item", grantedAt: Date.now()
      });
    });
    toast("Purchased! Check your titles.");
    myTitles.add(itemId);
    renderShop();
  } catch (e) {
    toast(e.message && e.message.length < 60 ? e.message : "Purchase failed");
    btn.disabled = false; btn.textContent = "BUY";
  }
}

/* =====================================================================
 * PROFILE
 * ===================================================================== */
function renderProfile() {
  if (!profile) return;
  const score = Number(profile.score || 0);
  const r = rankFor(score);
  $("pf-name").textContent = profile.name || displayNameOf(me.email || me.uid);
  $("pf-role").textContent = (profile.gameRole || profile.region || "PLAYER").toUpperCase();
  $("pf-rank-init").textContent = r.name.charAt(0);
  $("pf-rankhex").style.background = `linear-gradient(160deg, ${r.color}66, ${r.color}22)`;
  $("pf-rankhex").style.color = r.color;
  if (document.activeElement !== $("pf-game-role")) $("pf-game-role").value = profile.gameRole || "";
  if (document.activeElement !== $("pf-region")) $("pf-region").value = profile.region || "";
}
$("pf-save").onclick = async () => {
  const msg = $("pf-msg");
  try {
    // players self-update: role/status can never be changed by the player.
    await updateDoc(doc(db, "players", me.uid), {
      gameRole: $("pf-game-role").value.trim(),
      region: $("pf-region").value.trim()
    });
    msg.style.color = "var(--success)";
    msg.textContent = "Profile saved.";
  } catch (e) {
    msg.style.color = "var(--danger)";
    msg.textContent = "Save failed: " + (e.code || e.message).slice(0, 60);
  }
  setTimeout(() => { msg.textContent = ""; }, 2500);
};

/* =====================================================================
 * WEB PUSH (service worker + VAPID)
 * ===================================================================== */
function urlBase64ToUint8Array(base64) {
  const pad = "=".repeat((4 - (base64.length % 4)) % 4);
  const b64 = (base64 + pad).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(b64);
  const arr = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) arr[i] = raw.charCodeAt(i);
  return arr;
}
async function savePushSubscription(sub) {
  if (!me) return;
  // players/{uid} self-update is already allowed by the rules - no backend.
  await updateDoc(doc(db, "players", me.uid), { webPush: JSON.stringify(sub.toJSON()) });
  $("push-btn").classList.add("on");
  toast("Push notifications on");
}
async function subscribePush() {
  if (!VAPID) {
    toast("Push key not configured yet");
    return;
  }
  const reg = await navigator.serviceWorker.register("sw.js");
  const existing = await reg.pushManager.getSubscription();
  const sub = existing || await reg.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(VAPID)
  });
  await savePushSubscription(sub);
}
function maybeAutoSubscribePush() {
  // Silent subscribe when the user already granted permission earlier.
  if (VAPID && "Notification" in window && Notification.permission === "granted" && me) {
    subscribePush().catch(() => {});
  }
}
$("push-btn").onclick = async () => {
  try {
    if (!("serviceWorker" in navigator) || !("PushManager" in window)) {
      toast("Push needs iOS 16.4+ after Add to Home Screen");
      return;
    }
    if (!VAPID) {
      const k = prompt("Paste the Web Push VAPID key (Firebase Console > Cloud Messaging > Web Push certificates):");
      if (k) { VAPID = k.trim(); localStorage.setItem("zs_vapid", VAPID); }
      if (!VAPID) return;
    }
    if (!("Notification" in window)) return;
    const perm = Notification.permission === "default"
      ? await Notification.requestPermission()
      : Notification.permission;
    if (perm !== "granted") { toast("Notifications are blocked in Settings"); return; }
    await subscribePush();
  } catch (e) {
    toast("Push failed: " + String(e.message || e).slice(0, 60));
  }
};

/* =====================================================================
 * START
 * ===================================================================== */
const params = new URLSearchParams(location.search);
const startScreen = params.get("screen");
window.addEventListener("load", () => {
  if (startScreen && TITLES[startScreen.toLowerCase()]) show(startScreen.toLowerCase());
  if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});
});
