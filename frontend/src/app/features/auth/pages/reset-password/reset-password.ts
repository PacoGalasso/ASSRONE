import {Component, inject, OnInit} from '@angular/core';
import {AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators} from '@angular/forms';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {AuthService} from '../../../../core/auth/services/auth-service';

function passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
  const newPassword = control.get('newPassword')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;
  return newPassword === confirmPassword ? null : {passwordsMismatch: true};
}

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.html',
  styleUrl: '../login/login.css',
})
export class ResetPassword implements OnInit {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  // Read once from the URL on init, kept only in memory for the lifetime of
  // this component, then the URL itself is scrubbed of the token (see
  // ngOnInit) — never written to localStorage/sessionStorage.
  private token: string | null = null;

  isLoading = false;
  tokenMissing = false;
  errorMessage = '';

  form = this.fb.nonNullable.group(
    {
      newPassword: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', Validators.required],
    },
    {validators: passwordsMatchValidator}
  );

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    this.tokenMissing = !this.token;

    if (this.token) {
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: {},
        replaceUrl: true,
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid || this.isLoading || !this.token) return;

    this.isLoading = true;
    this.errorMessage = '';

    const {newPassword} = this.form.getRawValue();
    this.authService.resetPassword({token: this.token, newPassword}).subscribe({
      next: () => {
        this.isLoading = false;
        // Deliberately no auto-login (AuthService.resetPassword never touches
        // auth state) — the user must authenticate fresh with the new password.
        this.router.navigate(['/login'], {queryParams: {passwordReset: '1'}});
      },
      error: (err) => {
        this.isLoading = false;
        this.errorMessage =
          err.status === 400 || err.status === 404 || err.status === 410
            ? 'Ce lien de réinitialisation est invalide ou a expiré. Merci de refaire une demande.'
            : 'Une erreur est survenue. Merci de réessayer.';
      },
    });
  }
}
