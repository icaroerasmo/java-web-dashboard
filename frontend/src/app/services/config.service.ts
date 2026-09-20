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
}
