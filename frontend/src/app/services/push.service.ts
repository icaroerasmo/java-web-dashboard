import { Injectable, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, Subject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PushService {
  private notificationClickedSubject = new Subject<string>();
  /** Emits the notification id when the user clicks a received browser notification. */
  notificationClicked$ = this.notificationClickedSubject.asObservable();

  constructor(private http: HttpClient, private zone: NgZone) {}

  /**
   * Registers the service worker, requests notification permission and
   * subscribes to Web Push (when supported). No-op on non-HTTPS origins,
   * where the Push API is unavailable.
   */
  async init(): Promise<void> {
    if (typeof window === 'undefined'
        || !('serviceWorker' in navigator)
        || !('PushManager' in window)
        || !('Notification' in window)) {
      return;
    }
    // Register the click-message listener early so it is active regardless of the
    // async push setup below (otherwise a click can be missed on slow connections).
    navigator.serviceWorker.addEventListener('message', (event: MessageEvent) => {
      const data = event.data;
      if (data && data.type === 'open-notification' && data.id) {
        this.zone.run(() => this.notificationClickedSubject.next(data.id));
      }
    });
    try {
      const { publicKey } = await firstValueFrom(
        this.http.get<{ publicKey: string }>('/api/push/public-key')
      );
      if (!publicKey) {
        return;
      }

      const registration = await navigator.serviceWorker.register('/sw.js');

      if (Notification.permission === 'default') {
        await Notification.requestPermission();
      }
      if (Notification.permission !== 'granted') {
        return;
      }

      let subscription = await registration.pushManager.getSubscription();
      if (!subscription) {
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: this.urlBase64ToUint8Array(publicKey)
        });
      }
      const json = subscription.toJSON();
      await firstValueFrom(this.http.post('/api/push/subscribe', {
        endpoint: json.endpoint,
        p256dh: json.keys?.['p256dh'] ?? null,
        auth: json.keys?.['auth'] ?? null
      }));
    } catch (e) {
      // Push setup is best-effort; ignore failures.
    }
  }

  private urlBase64ToUint8Array(base64String: string): Uint8Array {
    const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
    const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
    const rawData = window.atob(base64);
    const outputArray = new Uint8Array(rawData.length);
    for (let i = 0; i < rawData.length; ++i) {
      outputArray[i] = rawData.charCodeAt(i);
    }
    return outputArray;
  }
}
