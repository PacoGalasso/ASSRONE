// core/services/event-service.ts — remplace le fichier entier
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {CreateEventRequest, EventItem} from '../models/event.model';

@Injectable({ providedIn: 'root' })
export class EventService {
  private http = inject(HttpClient);
  private baseUrl = '/api/events';

  getUpcoming(): Observable<EventItem[]> {
    return this.http.get<EventItem[]>(`${this.baseUrl}/upcoming`);
  }

  getAll(): Observable<EventItem[]> {
    return this.http.get<EventItem[]>(this.baseUrl);
  }

  create(request: CreateEventRequest): Observable<EventItem> {
    return this.http.post<EventItem>(this.baseUrl, request);
  }
}
