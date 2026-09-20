import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ModuleInfo {
  name: string;
  type: 'java' | 'infra';
  status: 'UP' | 'DOWN';
}

export interface ModulesResponse {
  modules: ModuleInfo[];
}

@Injectable({ providedIn: 'root' })
export class ModulesService {

  constructor(private http: HttpClient) {}

  getModules(): Observable<ModulesResponse> {
    return this.http.get<ModulesResponse>('/api/modules');
  }
}
