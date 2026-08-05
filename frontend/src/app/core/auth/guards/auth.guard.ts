import {CanActivateFn, Router} from '@angular/router';
import {inject} from '@angular/core';
import {AuthService} from '../services/auth-service';

// Safe to check isLoggedIn() synchronously, with no explicit "wait for init"
// step here: the app initializer (restoreSessionInitializer, registered in
// main.ts) already ran POST /auth/refresh and settled before Angular created
// any component or activated any route, so isLoggedIn() already reflects the
// outcome of that restoration attempt by the time this guard ever runs.
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isLoggedIn()) {
    return true;
  }

  router.navigate(['/']);
  return false;
};
