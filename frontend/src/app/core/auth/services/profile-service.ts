import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {ChangePasswordRequest, UpdateProfileRequest, UserProfile} from '../models/profile.model';

@Injectable({providedIn: 'root'})
export class ProfileService {
  private http = inject(HttpClient);
  private baseUrl = '/api/profile';

  getProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(this.baseUrl);
  }

  updateProfile(request: UpdateProfileRequest): Observable<UserProfile> {
    return this.http.put<UserProfile>(this.baseUrl, request);
  }

  changePassword(request: ChangePasswordRequest): Observable<void> {
    return this.http.put<void>(`${this.baseUrl}/password`, request);
  }
}
