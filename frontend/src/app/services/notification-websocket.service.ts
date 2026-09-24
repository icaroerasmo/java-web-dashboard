import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { NotificationSummary } from './notification.service';

@Injectable({ providedIn: 'root' })
export class NotificationWebSocketService {

  private subject = new Subject<NotificationSummary>();
  private socket: WebSocket | null = null;
  private reconnectAttempts = 0;
  private reconnectTimer: any = null;
  private manuallyClosed = false;

  connect(): void {
    if (this.socket && (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING)) {
      return;
    }
    this.manuallyClosed = false;
    const proto = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
    const socket = new WebSocket(`${proto}${window.location.host}/ws/notifications`);
    this.socket = socket;

    socket.onmessage = (event) => {
      try {
        this.subject.next(JSON.parse(event.data));
      } catch {
        // ignore malformed payload
      }
    };

    socket.onclose = () => {
      this.socket = null;
      if (this.manuallyClosed) {
        return;
      }
      this.scheduleReconnect();
    };

    socket.onerror = () => {
      socket.close();
    };
  }

  disconnect(): void {
    this.manuallyClosed = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }

  messages(): Observable<NotificationSummary> {
    return this.subject.asObservable();
  }

  private scheduleReconnect(): void {
    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
    this.reconnectTimer = setTimeout(() => this.connect(), delay);
  }
}
