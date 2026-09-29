// Minimal static file server for the ZERO STRESS PWA - zero dependencies,
// so it runs anywhere Node runs (Freebuff preview, Firebase App Hosting, CI).
// Start: PORT=3000 node server.js
const http = require("http");
const fs = require("fs");
const path = require("path");

const ROOT = __dirname;
const PORT = Number(process.env.PORT || 3000);
const HOST = "0.0.0.0"; // required by managed previews

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".webmanifest": "application/manifest+json",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".woff2": "font/woff2"
};

const server = http.createServer((req, res) => {
  let urlPath = decodeURIComponent((req.url || "/").split("?")[0]);
  if (urlPath === "/") urlPath = "/index.html";
  const filePath = path.normalize(path.join(ROOT, urlPath));
  // Path traversal guard: only serve files inside web/.
  if (!filePath.startsWith(ROOT)) {
    res.writeHead(403); res.end("Forbidden"); return;
  }
  fs.readFile(filePath, (err, data) => {
    if (err) {
      // SPA-style fallback: unknown paths get the shell (deep links).
      fs.readFile(path.join(ROOT, "index.html"), (err2, shell) => {
        if (err2) { res.writeHead(404); res.end("Not found"); return; }
        res.writeHead(200, { "Content-Type": MIME[".html"], "Cache-Control": "no-cache" });
        res.end(shell);
      });
      return;
    }
    const ext = path.extname(filePath).toLowerCase();
    const cache = ext === ".html" || ext === ".webmanifest" || urlPath === "/sw.js"
      ? "no-cache"
      : "public, max-age=3600";
    res.writeHead(200, { "Content-Type": MIME[ext] || "application/octet-stream", "Cache-Control": cache });
    res.end(data);
  });
});

server.listen(PORT, HOST, () => {
  console.log(`ZERO STRESS PWA running at http://${HOST}:${PORT}`);
});
