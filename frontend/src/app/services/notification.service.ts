import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface NotificationSummary {
  id: string;
  sender: string;
  mediaType: string;
  kind: string | null;
  summary: string;
  fileId: string | null;
  filename: string | null;
  sentAt: string | null;
  timestamp: number;
  date: string | null;
  hour: string | null;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(private http: HttpClient) {}

  getNotifications(limit = 100, before?: number): Observable<NotificationSummary[]> {
    const params = before !== undefined ? `?limit=${limit}&before=${before}` : `?limit=${limit}`;
    return this.http.get<NotificationSummary[]>(`/api/notifications${params}`);
  }

  mediaUrl(fileId: string): string {
    return `/api/notifications/media/${encodeURIComponent(fileId)}`;
  }

  getMediaText(fileId: string): Observable<string> {
    return this.http.get(this.mediaUrl(fileId), { responseType: 'text' });
  }
}
