// core/services/document-service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {DocumentItem} from '../models/document.model';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private http = inject(HttpClient);
  private baseUrl = '/api/documents';

  getAll(): Observable<DocumentItem[]> {
    return this.http.get<DocumentItem[]>(this.baseUrl);
  }

  upload(file: File, title: string, description: string): Observable<DocumentItem> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', title);
    formData.append('description', description);
    return this.http.post<DocumentItem>(this.baseUrl, formData);
  }

  download(id: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/download`, { responseType: 'blob' });
  }
}
