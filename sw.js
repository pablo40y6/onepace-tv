const CACHE_PREFIX = 'onepace-tv-preview-';
const CACHE_NAME = CACHE_PREFIX + 'shell-v2';
const CORE_FILES = ['index.html', 'style.css', 'app.js', 'catalog.js', 'progress.js', 'remote.js', 'manifest.json'];
const scope = new URL(self.registration.scope);
const local = name => new URL(name, scope).href;
const index = local('index.html');
const localFiles = new Set([...CORE_FILES, 'icon-192.png', 'icon-512.png'].map(local));

self.addEventListener('install', event => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE_NAME);
    const requests = CORE_FILES.map(name => new Request(local(name), { cache: 'reload' }));
    await cache.addAll(requests);
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', event => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys
      .filter(key => key.startsWith(CACHE_PREFIX) && key !== CACHE_NAME)
      .map(key => caches.delete(key)));
    await self.clients.claim();
  })());
});

async function networkFirst(request, cacheKey) {
  try {
    const response = await fetch(request, { cache: 'no-cache' });
    if (response.status >= 500) throw new Error('Server unavailable');
    if (response.ok) {
      try {
        const cache = await caches.open(CACHE_NAME);
        await cache.put(cacheKey, response.clone());
      } catch {
        // Keep serving fresh responses if the browser cannot update its cache.
      }
    }
    return response;
  } catch {
    try {
      return (await (await caches.open(CACHE_NAME)).match(cacheKey)) ?? Response.error();
    } catch {
      return Response.error();
    }
  }
}

self.addEventListener('fetch', event => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== scope.origin) return;
  if (request.mode === 'navigate' && (url.pathname === scope.pathname || url.pathname === new URL(index).pathname)) {
    event.respondWith(networkFirst(request, index));
  } else if (localFiles.has(url.href)) {
    event.respondWith(networkFirst(request, url.href));
  }
});
