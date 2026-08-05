import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router} from '@angular/router';
import {adminGuard} from './admin.guard';
import {AuthService} from '../services/auth-service';
import {AuthResponse} from '../models/auth.models';

describe('adminGuard', () => {
  let router: Router;
  let authService: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    router = TestBed.inject(Router);
    authService = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should allow activation for a logged-in admin restored at startup', () => {
    // #given
    const admin: AuthResponse = {token: 'access-token', username: 'admin@assrone.ch', role: 'ROLE_ADMIN'};
    authService.restoreSession().subscribe();
    httpMock.expectOne('/auth/refresh').flush(admin);

    // #when
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));

    // #then
    expect(result).toBe(true);
  });

  it('should deny activation and redirect home for a logged-in non-admin user', () => {
    // #given
    const member: AuthResponse = {token: 'access-token', username: 'membre@assrone.ch', role: 'ROLE_USER'};
    authService.restoreSession().subscribe();
    httpMock.expectOne('/auth/refresh').flush(member);
    const navigateSpy = vi.spyOn(router, 'navigate');

    // #when
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));

    // #then
    expect(result).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/']);
  });

  it('should deny activation when not logged in', () => {
    // #given no login/restoration performed
    const navigateSpy = vi.spyOn(router, 'navigate');

    // #when
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));

    // #then
    expect(result).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/']);
  });
});
