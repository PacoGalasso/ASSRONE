// features/contact/contact.ts
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {ContactService} from '../../core/auth/services/contact-service';

@Component({
  selector: 'app-contact',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './contact.html',
})
export class Contact {
  private fb = inject(FormBuilder);
  private contactService = inject(ContactService);

  submitting = signal(false);
  submitted = signal(false);
  errorMessage = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    subject: ['', Validators.required],
    message: ['', Validators.required],
  });

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.contactService.send(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitted.set(true);
        this.submitting.set(false);
      },
      error: () => {
        this.errorMessage.set('Une erreur est survenue. Réessayez ou écrivez-nous directement.');
        this.submitting.set(false);
      },
    });
  }
}
