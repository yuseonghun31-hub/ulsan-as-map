/* 울산 AS 지도 — 앱 껍데기 캐시 (화면 파일·아이콘만, 시트 자료와 지도 타일은 캐시하지 않는다) */
const CACHE = "ulsan-as-map-v31";
const SCOPE = self.registration.scope;
const SHELL = [
  SCOPE,
  SCOPE + "index.html",
  SCOPE + "manifest.json",
  SCOPE + "icon-192.png",
  SCOPE + "icon-512.png",
  SCOPE + "icon-180.png"
];

// 캐시에 넣을 지도 화면이면 그 열쇠(주소), 아니면 null
function pageKey(url) {
  const scopePath = new URL(SCOPE).pathname;
  if (url.pathname === scopePath || url.pathname === scopePath + "index.html") return SCOPE + "index.html";
  if (url.pathname.startsWith(scopePath) && /^u-[a-z0-9]{2,16}\.html$/.test(url.pathname.slice(scopePath.length))) return url.origin + url.pathname;
  return null;
}

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
  //  - 지도 화면(index.html·동료 지도 u-○○.html)만 각자 자기 이름으로 캐시한다
  //    (share.html 같은 다른 페이지가 index.html 자리를 덮지 않게)
  //  - 브라우저 HTTP 캐시(GitHub Pages 10분)를 건너뛰어, 고친 화면이 바로 보이게 한다
  //  - 오류 응답(404 등)은 캐시하지 않는다
  if (req.mode === "navigate") {
    const key = pageKey(url);
    e.respondWith(
      fetch(url.href, { cache: "no-cache", credentials: "same-origin" })
        .then((res) => {
          // 주소가 넘어간(redirect) 응답은 화면 열기에 그대로 쓸 수 없으므로 브라우저가 새 주소로 다시 가게 한다
          if (res && res.redirected) return Response.redirect(res.url, 302);
          if (key && res && res.ok) {
            const copy = res.clone();
            caches.open(CACHE).then((c) => c.put(key, copy)).catch(() => {});
          }
          return res;
        })
        .catch(() => (key ? caches.match(key) : Promise.resolve(null))
          .then((r) => r || caches.match(SCOPE + "index.html"))
          .then((r) => r || caches.match(SCOPE)))
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
