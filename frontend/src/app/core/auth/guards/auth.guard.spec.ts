import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router} from '@angular/router';
import {authGuard} from './auth.guard';

describe('authGuard', () => {
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  it('should allow activation when the user is logged in', () => {
    // #given: AuthService is constructed for the first time here, so it reads
    // this token from storage at injection time.
    localStorage.setItem('auth_token', 'access-token');

    // #when
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

    // #then
    expect(result).toBe(true);
  });

  it('should deny activation and redirect home when the user is not logged in', () => {
    // #given no token in storage
    const navigateSpy = vi.spyOn(router, 'navigate');

    // #when
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

    // #then
    expect(result).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/']);
  });
});
