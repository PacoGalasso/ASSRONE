// core/services/contact-service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {ContactMessageRequest} from '../models/contact.model';

@Injectable({ providedIn: 'root' })
export class ContactService {
  private http = inject(HttpClient);

  send(request: ContactMessageRequest): Observable<void> {
    return this.http.post<void>('/api/contact', request);
  }
}
