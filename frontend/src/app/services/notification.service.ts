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
  size: number | null;
}

export interface NotificationPage {
  items: NotificationSummary[];
  nextCursor: string | null;
  hasMore: boolean;
}

export type NotificationType = 'notifications' | 'logs';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(private http: HttpClient) {}

  getNotifications(
    type: NotificationType,
    limit = 100,
    cursor?: string,
    text?: string,
    kind?: string,
    date?: string,
    hour?: string
  ): Observable<NotificationPage> {
    let params = `type=${type}&limit=${limit}`;
    if (cursor) {
      params += `&cursor=${encodeURIComponent(cursor)}`;
    }
    if (text) {
      params += `&text=${encodeURIComponent(text)}`;
    }
    if (kind) {
      params += `&kind=${encodeURIComponent(kind)}`;
    }
    if (date) {
      params += `&date=${encodeURIComponent(date)}`;
    }
    if (hour) {
      params += `&hour=${encodeURIComponent(hour)}`;
    }
    return this.http.get<NotificationPage>(`/api/notifications?${params}`);
  }

  mediaUrl(fileId: string): string {
    return `/api/notifications/media/${encodeURIComponent(fileId)}`;
  }

  getMediaText(fileId: string): Observable<string> {
    return this.http.get(this.mediaUrl(fileId), { responseType: 'text' });
  }
}
