// Service de notifications de la trésorerie : affiche les alertes (son, vibration, pastille) et ouvre
// la bonne page quand on touche la notification. Aucune mise en cache : le site reste toujours à jour.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil(self.clients.claim()));
// Notification envoyée par la base (Web Push), reçue même page fermée : titre, texte, page à ouvrir
self.addEventListener('push', (e) => {
  let m = {}; try { m = e.data ? e.data.json() : {}; } catch { m = { titre: e.data?.text() }; }
  e.waitUntil(Promise.all([
    self.registration.showNotification(m.titre || 'Nouveauté', { body: m.texte || '', icon: 'icon-192.png', badge: 'icon-192.png',
      tag: m.tag || 'nouveaute', renotify: true, vibrate: [180, 90, 180], data: { url: m.url || './' } }),
    // Pastille sur l'icône de l'application installée : nombre de notifications affichées
    self.registration.getNotifications().then((l) => self.navigator.setAppBadge?.(l.length + 1)).catch(() => {}),
  ]));
});
self.addEventListener('notificationclick', (e) => {
  self.navigator.clearAppBadge?.().catch?.(() => {});
  e.notification.close();
  const url = new URL(e.notification.data?.url || './', self.registration.scope).href;
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((liste) => {
    const fen = liste.find((c) => c.url.startsWith(self.registration.scope));
    if (fen) { fen.focus(); return fen.navigate ? fen.navigate(url) : null; }
    return self.clients.openWindow(url);
  }));
});
