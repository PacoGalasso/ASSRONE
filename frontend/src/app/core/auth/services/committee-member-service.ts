// core/services/committee-member-service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CommitteeMemberDto, CommitteeMemberFormValue } from '../models/committee-member.model';

@Injectable({ providedIn: 'root' })
export class CommitteeMemberService {
  private http = inject(HttpClient);
  private baseUrl = '/api/committee-members';

  getPublicMembers(): Observable<CommitteeMemberDto[]> {
    return this.http.get<CommitteeMemberDto[]>(this.baseUrl);
  }

  getAllForAdmin(): Observable<CommitteeMemberDto[]> {
    return this.http.get<CommitteeMemberDto[]>(`${this.baseUrl}/admin`);
  }

  create(request: CommitteeMemberFormValue): Observable<CommitteeMemberDto> {
    return this.http.post<CommitteeMemberDto>(this.baseUrl, request);
  }

  update(id: number, request: CommitteeMemberFormValue): Observable<CommitteeMemberDto> {
    return this.http.put<CommitteeMemberDto>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  uploadPhoto(id: number, file: File): Observable<CommitteeMemberDto> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<CommitteeMemberDto>(`${this.baseUrl}/${id}/photo`, formData);
  }

  deletePhoto(id: number): Observable<CommitteeMemberDto> {
    return this.http.delete<CommitteeMemberDto>(`${this.baseUrl}/${id}/photo`);
  }

  photoUrl(id: number): string {
    return `${this.baseUrl}/${id}/photo`;
  }
}
