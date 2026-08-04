// features/events/create-event/create-event.ts
import {Component, inject, signal} from '@angular/core';
import {Router} from '@angular/router';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {EventService} from '../../core/auth/services/event-service';

@Component({
  selector: 'app-create-event',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './create-event.html',
})
export class CreateEvent {
  submitting = signal(false);
  errorMessage = signal<string | null>(null);
  private fb = inject(FormBuilder);
  form = this.fb.nonNullable.group({
    title: ['', Validators.required],
    description: ['', Validators.required],
    type: ['', [Validators.required, Validators.maxLength(80)]],
    eventDate: ['', Validators.required],
    startTime: ['', Validators.required],
    endTime: ['', Validators.required],
    location: ['', Validators.required],
    maxParticipants: [20, [Validators.required, Validators.min(1)]],
  });
  private eventService = inject(EventService);
  private router = inject(Router);

  onSubmit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const value = this.form.getRawValue();
    this.eventService.create({...value, type: value.type.trim()}).subscribe({
      next: () => this.router.navigate(['/events']),
      error: () => {
        this.errorMessage.set("Une erreur est survenue. Vérifiez les champs et réessayez.");
        this.submitting.set(false);
      },
    });
  }
}
