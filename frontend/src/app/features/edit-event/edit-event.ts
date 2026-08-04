// features/edit-event/edit-event.ts
import {Component, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {EventService} from '../../core/auth/services/event-service';

@Component({
  selector: 'app-edit-event',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './edit-event.html',
})
export class EditEvent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private eventService = inject(EventService);
  private fb = inject(FormBuilder);

  eventId = Number(this.route.snapshot.paramMap.get('id'));
  loading = signal(true);
  submitting = signal(false);
  loadErrorMessage = signal<string | null>(null);
  errorMessage = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    title: ['', Validators.required],
    description: ['', Validators.required],
    type: ['', [Validators.required, Validators.maxLength(80)]],
    eventDate: ['', Validators.required],
    startTime: ['', Validators.required],
    endTime: ['', Validators.required],
    location: ['', Validators.required],
    maxParticipants: [1, [Validators.required, Validators.min(1)]],
  });

  ngOnInit(): void {
    this.eventService.getById(this.eventId).subscribe({
      next: (event) => {
        this.form.patchValue({
          title: event.title,
          description: event.description,
          type: event.type,
          eventDate: event.eventDate,
          startTime: event.startTime.slice(0, 5),
          endTime: event.endTime.slice(0, 5),
          location: event.location,
          maxParticipants: event.maxParticipants,
        });
        this.loading.set(false);
      },
      error: () => {
        this.loadErrorMessage.set("Impossible de charger cet événement.");
        this.loading.set(false);
      },
    });
  }

  onSubmit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const value = this.form.getRawValue();
    this.eventService.update(this.eventId, {...value, type: value.type.trim()}).subscribe({
      next: () => this.router.navigate(['/events']),
      error: (err) => {
        this.errorMessage.set(this.updateErrorMessage(err.status));
        this.submitting.set(false);
      },
    });
  }

  private updateErrorMessage(status: number): string {
    if (status === 400) {
      return 'Certains champs sont invalides. Vérifiez le formulaire.';
    }
    if (status === 403) {
      return "Vous n'êtes pas autorisé à modifier cet événement.";
    }
    if (status === 404) {
      return "Cet événement n'existe plus.";
    }
    if (status === 409) {
      return "Le nombre de places ne peut pas être inférieur au nombre d'inscrits actuels.";
    }
    return 'Une erreur est survenue. Vérifiez les champs et réessayez.';
  }
}
