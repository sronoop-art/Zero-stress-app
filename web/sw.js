/* ZERO STRESS PWA service worker.
 *
 * Two jobs:
 *  1. Web Push: receive pushes for web users (iOS 16.4+ requires the SW to
 *     exist at scope "/" and requires showNotification from a push event).
 *  2. Offline shell: stale-while-revalidate for same-origin static assets so
 *     the app opens instantly; Firebase calls are NEVER cached (network only).
 */
const CACHE = "zs-shell-v1";

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE).then((c) => c.addAll([
      "/", "/index.html", "/styles.css", "/app.js", "/manifest.webmanifest",
      "/icons/icon-192.png", "/icons/icon-512.png"
    ])).then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  // Firestore/Auth/push endpoints and anything cross-origin: straight network.
  if (url.origin !== self.location.origin) return;
  // Same-origin static shell: serve from cache, refresh in the background.
  if (event.request.method === "GET") {
    event.respondWith(
      caches.open(CACHE).then(async (cache) => {
        const cached = await cache.match(event.request);
        const network = fetch(event.request).then((res) => {
          if (res && res.ok) cache.put(event.request, res.clone());
          return res;
        }).catch(() => cached);
        return cached || network;
      })
    );
  }
});

// ---------------------------- Web Push ----------------------------
self.addEventListener("push", (event) => {
  let payload = {};
  try { payload = event.data ? event.data.json() : {}; } catch (e) { payload = {}; }
  const title = payload.title || "ZERO STRESS";
  const body = payload.body || payload.message || "";
  const type = payload.type || "general";
  // Deep-link tags: collapse per conversation, click lands on the right screen.
  const tag = payload.threadId ? ("dm-" + payload.threadId) : ("zs-" + type);
  event.waitUntil((async () => {
    try {
      await self.registration.showNotification(title, {
        body,
        icon: "/icons/icon-192.png",
        badge: "/icons/icon-192.png",
        tag,
        renotify: true,
        data: { type: type, threadId: payload.threadId || null, screen: payload.screen || null }
      });
    } catch (e) {
      // iOS <16.4 or notification permission revoked - nothing else to do.
    }
  })());
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const data = event.notification.data || {};
  let target = "/";
  if (data.threadId) target = "/?screen=dms&thread=" + encodeURIComponent(data.threadId);
  else if (data.screen) target = "/?screen=" + encodeURIComponent(data.screen);
  event.waitUntil((async () => {
    const all = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
    for (const client of all) {
      if (client.url.startsWith(self.location.origin)) {
        await client.focus();
        client.navigate(target).catch(() => {});
        return;
      }
    }
    await self.clients.openWindow(target);
  })());
});
