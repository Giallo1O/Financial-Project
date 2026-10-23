// Service worker minimo: serve a rendere l'app installabile e ad avviarla in fretta.
// Non salva MAI dati finanziari: le chiamate /api e tutto cio' che non e' GET o non e' dello stesso sito vanno sempre in rete.
const SHELL = 'finmanager-shell-v1';
const ASSETS = 'finmanager-assets-v1';
const SHELL_FILES = ['/', '/manifest.webmanifest', '/icon.svg', '/icon-192.png'];
const MAX_ASSETS = 60; // i file in /assets hanno il nome con hash: a ogni nuova versione se ne aggiungono, quindi si tiene un massimo

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(SHELL).then((cache) => cache.addAll(SHELL_FILES)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== SHELL && k !== ASSETS).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

async function remember(cacheName, request, response) {
  const cache = await caches.open(cacheName);
  await cache.put(request, response);
  if (cacheName === ASSETS) {
    const keys = await cache.keys();
    if (keys.length > MAX_ASSETS) await cache.delete(keys[0]);
  }
}

self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;

  // Pagine: prima la rete (cosi' dopo un aggiornamento si vede subito la versione nuova); senza rete, la pagina salvata.
  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).catch(() => caches.match('/')));
    return;
  }

  // File con hash (JS/CSS): non cambiano mai, quindi prima la copia salvata.
  if (url.pathname.startsWith('/assets/')) {
    event.respondWith(
      caches.match(request).then((hit) => hit || fetch(request).then((response) => {
        if (response.ok) event.waitUntil(remember(ASSETS, request, response.clone()));
        return response;
      }))
    );
    return;
  }

  // Icone e manifest: rete, con copia salvata se manca.
  event.respondWith(fetch(request).then((response) => {
    if (response.ok) event.waitUntil(remember(SHELL, request, response.clone()));
    return response;
  }).catch(() => caches.match(request)));
});
