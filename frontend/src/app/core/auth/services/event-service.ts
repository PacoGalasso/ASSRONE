// core/services/event-service.ts — remplace le fichier entier
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {CreateEventRequest, EventItem, EventRegistrationRequest} from '../models/event.model';

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

  register(eventId: number, request: EventRegistrationRequest): Observable<EventItem> {
    return this.http.post<EventItem>(`${this.baseUrl}/${eventId}/register`, request);
  }

  delete(eventId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${eventId}`);
  }
}
