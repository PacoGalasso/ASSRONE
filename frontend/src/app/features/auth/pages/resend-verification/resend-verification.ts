import {Component, inject} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {AuthService} from '../../../../core/auth/services/auth-service';

@Component({
  selector: 'app-resend-verification',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './resend-verification.html',
  styleUrl: '../login/login.css',
})
export class ResendVerification {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);

  isLoading = false;
  submitted = false;

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  onSubmit(): void {
    if (this.form.invalid || this.isLoading) return;

    this.isLoading = true;
    // Anti-enumeration: the backend always returns the same generic outcome,
    // and this page shows the same confirmation on both success and error.
    this.authService.resendVerification(this.form.getRawValue()).subscribe({
      next: () => this.finish(),
      error: () => this.finish(),
    });
  }

  private finish(): void {
    this.isLoading = false;
    this.submitted = true;
  }
}
