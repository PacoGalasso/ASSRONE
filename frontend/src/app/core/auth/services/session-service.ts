import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {SessionListResponse, SessionsRevokedResponse} from '../models/session.model';

@Injectable({providedIn: 'root'})
export class SessionService {
  private http = inject(HttpClient);
  private baseUrl = '/api/me/sessions';

  list(): Observable<SessionListResponse> {
    return this.http.get<SessionListResponse>(this.baseUrl);
  }

  revoke(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  revokeOthers(): Observable<SessionsRevokedResponse> {
    return this.http.delete<SessionsRevokedResponse>(`${this.baseUrl}/others`);
  }

  revokeAll(): Observable<void> {
    return this.http.delete<void>(this.baseUrl);
  }
}
