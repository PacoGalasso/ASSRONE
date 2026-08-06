import {Component, inject, OnInit} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {AuthService} from '../../../../core/auth/services/auth-service';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink
  ],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login implements OnInit {
  loginForm!: FormGroup;
  isLoading = false;
  errorMessage = '';
  passwordResetSuccess = false;
  // True only for the 403 EMAIL_NOT_VERIFIED case — a distinct state from a
  // generic errorMessage so the template can offer a "resend verification"
  // link instead of just "connexion impossible".
  emailNotVerified = false;
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  ngOnInit(): void {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]]
    });

    if (this.route.snapshot.queryParamMap.get('passwordReset') === '1') {
      this.passwordResetSuccess = true;
      this.router.navigate([], {relativeTo: this.route, queryParams: {}, replaceUrl: true});
    }
  }

  onSubmit(): void {
    if (this.loginForm.invalid) return;

    this.isLoading = true;
    this.errorMessage = '';
    this.emailNotVerified = false;

    this.authService.login(this.loginForm.value).subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.isLoading = false;
        // 403 here is exclusively EmailNotVerifiedException (see UserController):
        // a wrong password/locked account is always 401. Never routed through
        // the interceptor's refresh flow either — /auth/generateToken is a
        // public endpoint (see jwt-interceptor.ts's PUBLIC_AUTH_ENDPOINTS).
        if (err.status === 403) {
          this.emailNotVerified = true;
          this.errorMessage = err.error?.error || 'Veuillez vérifier votre adresse email avant de vous connecter.';
          return;
        }
        this.errorMessage = err.error?.error || 'Erreur de connexion';
      }
    });
  }
}
