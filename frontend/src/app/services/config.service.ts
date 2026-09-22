import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ConfigService {

  constructor(private http: HttpClient) {}

  getConfig(name: string): Observable<any> {
    return this.http.get<any>(`/api/modules/${name}/config`);
  }

  saveConfig(name: string, config: any): Observable<any> {
    return this.http.put<any>(`/api/modules/${name}/config`, config);
  }

  restartModule(name: string): Observable<any> {
    return this.http.post<any>(`/api/modules/${name}/restart`, {});
  }

  getEnv(name: string): Observable<any> {
    return this.http.get<any>(`/api/modules/${name}/env`);
  }

  saveEnv(name: string, envVars: any): Observable<any> {
    return this.http.put<any>(`/api/modules/${name}/env`, envVars);
  }

  getGlobalEnv(): Observable<any> {
    return this.http.get<any>('/api/env');
  }

  saveGlobalEnv(envVars: any): Observable<any> {
    return this.http.put<any>('/api/env', envVars);
  }

  getGo2RtcStreams(): Observable<any> {
    return this.http.get<any>('/api/go2rtc/streams');
  }

  saveGo2RtcStream(name: string, url: string): Observable<any> {
    return this.http.put<any>('/api/go2rtc/streams', { name, url });
  }

  removeGo2RtcStream(name: string): Observable<any> {
    return this.http.delete<any>(`/api/go2rtc/streams/${encodeURIComponent(name)}`);
  }

  restartGo2Rtc(): Observable<any> {
    return this.http.post<any>('/api/go2rtc/restart', {});
  }

  getRabbitMqQueues(): Observable<any> {
    return this.http.get<any>('/api/rabbitmq/queues');
  }

  getRabbitMqMessages(queue: string, count: number): Observable<any> {
    return this.http.get<any>(`/api/rabbitmq/queues/${encodeURIComponent(queue)}/messages?count=${count}`);
  }

  sendRabbitMqMessage(queue: string, payload: string): Observable<any> {
    return this.http.post<any>(`/api/rabbitmq/queues/${encodeURIComponent(queue)}/messages`, { payload });
  }

  removeRabbitMqMessages(queue: string, count?: number): Observable<any> {
    const path = `/api/rabbitmq/queues/${encodeURIComponent(queue)}/messages`;
    return count
      ? this.http.delete<any>(`${path}?count=${count}`)
      : this.http.delete<any>(path);
  }

  restartRabbitMq(): Observable<any> {
    return this.http.post<any>('/api/rabbitmq/restart', {});
  }

  getDetections(): Observable<any> {
    return this.http.get<any>('/api/detections');
  }
}
