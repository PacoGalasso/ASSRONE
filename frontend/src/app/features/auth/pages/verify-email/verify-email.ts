import {Component, inject, OnInit, signal} from '@angular/core';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {AuthService} from '../../../../core/auth/services/auth-service';

type VerifyState = 'verifying' | 'success' | 'invalid' | 'missing-token';

@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './verify-email.html',
  styleUrl: '../login/login.css',
})
export class VerifyEmail implements OnInit {
  private authService = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  state = signal<VerifyState>('verifying');

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');

    if (!token) {
      this.state.set('missing-token');
      return;
    }

    // Read once, then scrub the token from the URL immediately — it is not
    // retained anywhere beyond this in-flight request.
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {},
      replaceUrl: true,
    });

    this.authService.verifyEmail({token}).subscribe({
      next: () => this.state.set('success'),
      error: () => this.state.set('invalid'),
    });
  }
}
