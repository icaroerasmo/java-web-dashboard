import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PushService {
  constructor(private http: HttpClient) {}

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
      await firstValueFrom(this.http.post('/api/push/subscribe', subscription.toJSON()));
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
