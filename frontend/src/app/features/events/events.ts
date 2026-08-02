// features/events/events.ts
import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { typeLabel, eventDay, eventMonth } from './event-display.util';
import {EventService} from '../../core/auth/services/event-service';
import {EventItem} from '../../core/auth/models/event.model';
import {AuthService} from '../../core/auth/services/auth-service';

@Component({
  selector: 'app-events',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule],
  templateUrl: './events.html',
})
class Events implements OnInit {
  private eventService = inject(EventService);
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);

  events = signal<EventItem[]>([]);
  loading = signal(true);
  openRegistrationId = signal<number | null>(null);
  registering = signal(false);
  registrationError = signal<string | null>(null);
  registeredIds = signal<Set<number>>(new Set());

  readonly typeLabel = typeLabel;
  readonly day = eventDay;
  readonly month = eventMonth;
  readonly isAdmin = () => this.authService.isAdmin();

  registrationForm = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
  });

  ngOnInit(): void {
    this.eventService.getAll().subscribe({
      next: (data) => {
        this.events.set(data);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  toggleRegistration(eventId: number): void {
    this.registrationError.set(null);
    this.registrationForm.reset();
    this.openRegistrationId.set(this.openRegistrationId() === eventId ? null : eventId);
  }

  onRegister(event: EventItem): void {
    if (this.registrationForm.invalid) {
      this.registrationForm.markAllAsTouched();
      return;
    }

    this.registering.set(true);
    this.registrationError.set(null);

    this.eventService.register(event.id, this.registrationForm.getRawValue()).subscribe({
      next: (updated) => {
        this.events.update(list => list.map(e => e.id === updated.id ? updated : e));
        this.registeredIds.update(ids => new Set(ids).add(event.id));
        this.openRegistrationId.set(null);
        this.registering.set(false);
      },
      error: (err) => {
        this.registrationError.set(
          err.status === 409 ? 'Cet événement est complet.' : 'Une erreur est survenue. Réessayez.'
        );
        this.registering.set(false);
      },
    });
  }
}

export default Events
