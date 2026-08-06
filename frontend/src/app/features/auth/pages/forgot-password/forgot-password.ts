import {Component, inject} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {AuthService} from '../../../../core/auth/services/auth-service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './forgot-password.html',
  styleUrl: '../login/login.css',
})
export class ForgotPassword {
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
    this.authService.forgotPassword(this.form.getRawValue()).subscribe({
      // The backend always returns the same generic outcome regardless of
      // whether the account exists — this page shows the same confirmation
      // regardless of the response too, on both success and error, so a
      // 429 (rate limit) or any other failure never leaks anything either.
      next: () => this.finish(),
      error: () => this.finish(),
    });
  }

  private finish(): void {
    this.isLoading = false;
    this.submitted = true;
  }
}
