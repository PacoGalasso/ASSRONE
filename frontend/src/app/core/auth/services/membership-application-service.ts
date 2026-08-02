// core/services/membership-application-service.ts
import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {CreateMembershipApplicationRequest, MembershipApplicationDto} from '../models/membership-application.model';


@Injectable({providedIn: 'root'})
export class MembershipApplicationService {
  private http = inject(HttpClient);
  private baseUrl = '/api/membership-applications';

  submit(request: CreateMembershipApplicationRequest): Observable<MembershipApplicationDto> {
    return this.http.post<MembershipApplicationDto>(this.baseUrl, request);
  }

  getAll(): Observable<MembershipApplicationDto[]> {
    return this.http.get<MembershipApplicationDto[]>(this.baseUrl);
  }

  accept(id: number): Observable<MembershipApplicationDto> {
    return this.http.post<MembershipApplicationDto>(`${this.baseUrl}/${id}/accept`, {});
  }

  reject(id: number): Observable<MembershipApplicationDto> {
    return this.http.post<MembershipApplicationDto>(`${this.baseUrl}/${id}/reject`, {});
  }
}
