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
  sentAt: string;
  timestamp: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  constructor(private http: HttpClient) {}

  getNotifications(limit = 300): Observable<NotificationSummary[]> {
    return this.http.get<NotificationSummary[]>(`/api/notifications?limit=${limit}`);
  }

  mediaUrl(fileId: string): string {
    return `/api/notifications/media/${encodeURIComponent(fileId)}`;
  }
}
