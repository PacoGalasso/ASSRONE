// core/services/admin-user-service.ts
import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {UserProfile} from '../models/profile.model';

@Injectable({providedIn: 'root'})
export class AdminUserService {
  private http = inject(HttpClient);
  private baseUrl = '/api/users';

  getAll(): Observable<UserProfile[]> {
    return this.http.get<UserProfile[]>(this.baseUrl);
  }
}
