// Service de notifications de la trésorerie : affiche les alertes (son, vibration, pastille) et ouvre
// la bonne page quand on touche la notification. Aucune mise en cache : le site reste toujours à jour.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));
self.addEventListener('notificationclick', (e) => {
  e.notification.close();
  const url = new URL(e.notification.data?.url || './', self.registration.scope).href;
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((liste) => {
    const fen = liste.find((c) => c.url.startsWith(self.registration.scope));
    if (fen) { fen.focus(); return fen.navigate ? fen.navigate(url) : null; }
    return self.clients.openWindow(url);
  }));
});
