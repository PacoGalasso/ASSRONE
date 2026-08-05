// core/services/admin-user-service.ts
import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {AdminUserDto, Role} from '../models/profile.model';

@Injectable({providedIn: 'root'})
export class AdminUserService {
  private http = inject(HttpClient);
  private baseUrl = '/api/users';

  getAll(): Observable<AdminUserDto[]> {
    return this.http.get<AdminUserDto[]>(this.baseUrl);
  }

  changeRole(id: number, role: Role): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/${id}/role`, {role});
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
