import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface VideoRecord {
  camera: string;
  filename: string;
  timestamp: string; // "yyyy-MM-dd HH:mm:ss"
  drivePath: string | null;
  localPath: string | null;
  size: number;
  local: boolean;
}

export interface RecordsPage {
  items: VideoRecord[];
  nextCursor: string | null;
  hasMore: boolean;
}

@Injectable({ providedIn: 'root' })
export class RecordService {
  constructor(private http: HttpClient) {}

  getRecords(camera?: string, date?: string, hour?: string, limit = 100, cursor?: string): Observable<RecordsPage> {
    let params = `limit=${limit}`;
    if (camera) {
      params += `&camera=${encodeURIComponent(camera)}`;
    }
    if (date) {
      params += `&date=${encodeURIComponent(date)}`;
    }
    if (hour !== undefined && hour !== null && hour !== '') {
      params += `&hour=${encodeURIComponent(String(hour))}`;
    }
    if (cursor) {
      params += `&cursor=${encodeURIComponent(cursor)}`;
    }
    return this.http.get<RecordsPage>(`/api/records?${params}`);
  }

  getCameras(): Observable<string[]> {
    return this.http.get<string[]>('/api/records/cameras');
  }

  streamUrl(filename: string): string {
    return `/api/records/${encodeURIComponent(filename)}/stream`;
  }

  thumbnailUrl(filename: string): string {
    return `/api/records/${encodeURIComponent(filename)}/thumbnail`;
  }
}
