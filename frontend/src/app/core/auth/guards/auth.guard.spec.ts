import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router} from '@angular/router';
import {authGuard} from './auth.guard';
import {AuthService} from '../services/auth-service';
import {AuthResponse} from '../models/auth.models';

describe('authGuard', () => {
  let router: Router;
  let authService: AuthService;
  let httpMock: HttpTestingController;

  const authResponse: AuthResponse = {token: 'access-token', username: 'membre@assrone.ch', role: 'ROLE_USER'};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    router = TestBed.inject(Router);
    authService = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should allow activation once session restoration placed a token in memory', () => {
    // #given: mirrors what the app initializer does at boot, before any guard runs
    authService.restoreSession().subscribe();
    httpMock.expectOne('/auth/refresh').flush(authResponse);

    // #when
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

    // #then
    expect(result).toBe(true);
  });

  it('should deny activation and redirect home when the user is not logged in', () => {
    // #given no successful login/restoration in this session
    const navigateSpy = vi.spyOn(router, 'navigate');

    // #when
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

    // #then
    expect(result).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/']);
  });

  it('should deny activation when the initial restoration attempt was rejected', () => {
    // #given: mirrors an expired/absent refresh cookie at boot
    authService.restoreSession().subscribe();
    httpMock.expectOne('/auth/refresh').flush({error: 'invalide'}, {status: 401, statusText: 'Unauthorized'});
    const navigateSpy = vi.spyOn(router, 'navigate');

    // #when
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));

    // #then
    expect(result).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/']);
  });
});
