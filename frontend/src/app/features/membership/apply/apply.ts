// features/membership/apply/apply.ts
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {MembershipApplicationService} from '../../../core/auth/services/membership-application-service';

@Component({
  selector: 'app-apply',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './apply.html',
})
export class Apply {
  private fb = inject(FormBuilder);
  private applicationService = inject(MembershipApplicationService);

  submitting = signal(false);
  submitted = signal(false);
  errorMessage = signal<string | null>(null);

  form = this.fb.nonNullable.group({
    fullName: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    phone: [''],
    membershipType: ['INDIVIDUEL' as const, Validators.required],
    message: [''],
    charterAccepted: [false, Validators.requiredTrue],
  });

  onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.applicationService.submit(this.form.getRawValue()).subscribe({
      next: () => {
        this.submitted.set(true);
        this.submitting.set(false);
      },
      error: (err) => {
        this.errorMessage.set(
          err.status === 409 && err.error?.error
            ? err.error.error
            : 'Une erreur est survenue. Vérifiez les champs et réessayez.'
        );
        this.submitting.set(false);
      },
    });
  }
}
