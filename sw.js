/* 울산 AS 지도 — 앱 껍데기 캐시 (화면 파일·아이콘만, 시트 자료와 지도 타일은 캐시하지 않는다) */
const CACHE = "ulsan-as-map-v29";
const SCOPE = self.registration.scope;
const SHELL = [
  SCOPE,
  SCOPE + "index.html",
  SCOPE + "manifest.json",
  SCOPE + "icon-192.png",
  SCOPE + "icon-512.png",
  SCOPE + "icon-180.png"
];

self.addEventListener("install", (e) => {
  e.waitUntil(
    caches.open(CACHE)
      .then((c) => c.addAll(SHELL))
      .catch(() => {})
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys
        .filter((k) => k !== CACHE)
        .map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  // 다른 도메인(네이버 지도 SDK·타일·시트 서버)은 그대로 지나간다
  if (url.origin !== self.location.origin) return;

  // 샘플 시연 페이지는 캐시에 넣지 않는다(실제 index.html 캐시를 덮지 않게)
  if (url.pathname.endsWith("/sample.html")) return;

  // 화면은 최신을 먼저 받고, 안 되면 캐시로 연다
  if (req.mode === "navigate") {
    e.respondWith(
      fetch(req)
        .then((res) => {
          const copy = res.clone();
          caches.open(CACHE).then((c) => c.put(SCOPE + "index.html", copy)).catch(() => {});
          return res;
        })
        .catch(() => caches.match(SCOPE + "index.html").then((r) => r || caches.match(SCOPE)))
    );
    return;
  }

  // 아이콘·manifest 는 캐시 우선
  e.respondWith(
    caches.match(req).then((hit) => hit || fetch(req).then((res) => {
      if (res && res.ok) {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(req, copy)).catch(() => {});
      }
      return res;
    }))
  );
});
