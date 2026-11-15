/* 울산 AS 지도 — 앱 껍데기 캐시 (화면 파일·아이콘만, 시트 자료는 캐시하지 않는다) */
const CACHE = "ulsan-as-map-v3";
const TILECACHE = "ulsan-as-map-tiles-v1";
const TILE_HOSTS = ["basemaps.cartocdn.com"];
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
        .filter((k) => k !== CACHE && k !== TILECACHE)
        .map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// 배경 지도 타일: 한 번 받은 타일은 캐시에서 꺼내 쓴다(같은 지역을 다시 열 때 데이터를 아낀다).
// 너무 쌓이면 오래된 것부터 지워 260장 정도만 남긴다.
async function tileResponse(req) {
  const c = await caches.open(TILECACHE);
  const hit = await c.match(req);
  if (hit) return hit;
  const res = await fetch(req);
  if (res && (res.ok || res.type === "opaque")) {
    try {
      await c.put(req, res.clone());
      const keys = await c.keys();
      if (keys.length > 260) {
        for (const k of keys.slice(0, keys.length - 260)) await c.delete(k);
      }
    } catch (e) {}
  }
  return res;
}

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);

  // 타일(다른 도메인)은 캐시 우선
  if (TILE_HOSTS.indexOf(url.hostname) >= 0) {
    e.respondWith(tileResponse(req).catch(() => fetch(req)));
    return;
  }
  if (url.origin !== self.location.origin) return; // 데이터 서버(Apps Script)는 그대로 지나간다

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
