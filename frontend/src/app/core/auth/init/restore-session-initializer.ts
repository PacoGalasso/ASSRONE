import {inject} from '@angular/core';
import {firstValueFrom} from 'rxjs';
import {AuthService} from '../services/auth-service';

// Registered once via provideAppInitializer (see main.ts). Angular blocks
// bootstrap — no component is created, no route is activated, no guard
// runs — until the returned promise settles, which guarantees exactly one
// POST /auth/refresh per app load and that every guard's isLoggedIn() check
// already reflects the outcome by the time it runs.
export function restoreSessionInitializer(): Promise<void> {
  const authService = inject(AuthService);
  return firstValueFrom(authService.restoreSession());
}
