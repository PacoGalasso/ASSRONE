// features/events/events.ts
import { Component, inject, signal, OnInit } from '@angular/core';
import { typeLabel, eventDay, eventMonth } from './event-display.util';
import {EventService} from '../../core/auth/services/event-service';
import {EventItem} from '../../core/auth/models/event.model';

@Component({
  selector: 'app-events',
  standalone: true,
  templateUrl: './events.html',
})
class Events implements OnInit {
  private eventService = inject(EventService);

  events = signal<EventItem[]>([]);
  loading = signal(true);

  readonly typeLabel = typeLabel;
  readonly day = eventDay;
  readonly month = eventMonth;

  ngOnInit(): void {
    this.eventService.getAll().subscribe({
      next: (data) => {
        this.events.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}

export default Events
