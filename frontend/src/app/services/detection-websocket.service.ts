import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class DetectionWebSocketService {

  private subject = new Subject<any[]>();
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
    const socket = new WebSocket(`${proto}${window.location.host}/ws/detections`);
    this.socket = socket;

    socket.onmessage = (event) => {
      try {
        const list = JSON.parse(event.data);
        this.subject.next(Array.isArray(list) ? list : []);
      } catch {
        this.subject.next([]);
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

  messages(): Observable<any[]> {
    return this.subject.asObservable();
  }

  private scheduleReconnect(): void {
    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
    this.reconnectTimer = setTimeout(() => this.connect(), delay);
  }
}