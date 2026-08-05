import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router} from '@angular/router';
import {adminGuard} from './admin.guard';

describe('adminGuard', () => {
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  it('should allow activation for a logged-in admin', () => {
    // #given: AuthService reads this token/user pair at its first injection below
    localStorage.setItem('auth_token', 'access-token');
    localStorage.setItem('auth_user', JSON.stringify({username: 'admin@assrone.ch', role: 'ROLE_ADMIN'}));

    // #when
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));

    // #then
    expect(result).toBe(true);
  });

  it('should deny activation and redirect home for a logged-in non-admin user', () => {
    // #given
    localStorage.setItem('auth_token', 'access-token');
    localStorage.setItem('auth_user', JSON.stringify({username: 'membre@assrone.ch', role: 'ROLE_USER'}));
    const navigateSpy = vi.spyOn(router, 'navigate');

    // #when
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));

    // #then
    expect(result).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/']);
  });

  it('should deny activation when not logged in, even if a stale ROLE_ADMIN user object remains in storage', () => {
    // #given: no auth_token, but a leftover admin user object (e.g. cleared inconsistently)
    localStorage.setItem('auth_user', JSON.stringify({username: 'admin@assrone.ch', role: 'ROLE_ADMIN'}));
    const navigateSpy = vi.spyOn(router, 'navigate');

    // #when
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));

    // #then
    expect(result).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/']);
  });
});
