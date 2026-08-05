import {CanActivateFn, Router} from '@angular/router';
import {inject} from '@angular/core';
import {AuthService} from '../services/auth-service';

// See auth.guard.ts: safe to check synchronously, no explicit wait needed —
// the app initializer already restored (or failed to restore) the session
// before this guard can ever run.
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn() && authService.isAdmin()) {
    return true;
  }

  router.navigate(['/']);
  return false;
};
