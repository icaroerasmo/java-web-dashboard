// Cafofo service worker — receives Web Push notifications and shows them even
// when the PWA is closed. No caching here: the dashboard is a LAN app.

// Activate new versions immediately (no stale-SW window where an old
// notificationclick handler keeps running).
self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch (e) {
    // non-JSON payload — use as-is
    data = { body: event.data ? event.data.text() : '' };
  }

  const options = {
    body: data.body || '',
    icon: '/assets/icons/icon-192.png',
    badge: '/assets/icons/icon-192.png',
    tag: data.tag || undefined,
    data: data,
    vibrate: [100, 50, 100]
  };

  event.waitUntil(self.registration.showNotification(data.title || 'Cafofo', options));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const id = (event.notification.data && event.notification.data.id) || '';
  const url = id ? ('/?notification=' + encodeURIComponent(id)) : '/';

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ('focus' in client) {
          if (id) {
            client.postMessage({ type: 'open-notification', id });
          }
          return client.focus();
        }
      }
      return self.clients.openWindow(url);
    })
  );
});
