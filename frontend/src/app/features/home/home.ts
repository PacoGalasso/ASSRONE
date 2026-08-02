// features/home/home.ts
import {Component, inject, OnInit, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {eventDay, eventMonth, typeLabel} from '../events/event-display.util';
import {EventService} from '../../core/auth/services/event-service';
import {EventItem} from '../../core/auth/models/event.model';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './home.html',
})
export class Home implements OnInit {
  upcomingEvents = signal<EventItem[]>([]);
  loading = signal(true);
  readonly day = eventDay;
  readonly month = eventMonth;
  readonly typeLabel = typeLabel;
  protected readonly services = [
    {
      title: 'Espace membres',
      description: "Un espace personnel où déposer vos travaux, retrouver vos ressources et échanger avec d'autres membres du réseau.",
      link: '/members-portal',
      icon: 'members',
    },
    {
      title: 'Formations et rencontres',
      description: "Des ateliers et conférences animés par des professionnel·le·s de terrain, pensés pour une application directe dans la pratique.",
      link: '/events',
      icon: 'formations',
    },
    {
      title: 'Statuts et chartes',
      description: "Les documents qui encadrent notre fonctionnement et nos engagements envers les membres et le public.",
      link: '/statutes',
      icon: 'documents',
    },
  ];
  private eventService = inject(EventService);

  ngOnInit(): void {
    this.eventService.getUpcoming().subscribe({
      next: (data) => {
        this.upcomingEvents.set(data.slice(0, 3));
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
