import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {restoreSessionInitializer} from './restore-session-initializer';
import {AuthService} from '../services/auth-service';
import {AuthResponse} from '../models/auth.models';

describe('restoreSessionInitializer', () => {
  let httpMock: HttpTestingController;
  let authService: AuthService;

  const authResponse: AuthResponse = {token: 'access-token', username: 'membre@assrone.ch', role: 'ROLE_USER'};

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  it('should issue exactly one POST /auth/refresh with credentials and no token in the body', async () => {
    // #given / #when
    const pending = TestBed.runInInjectionContext(() => restoreSessionInitializer());

    // #then
    const req = httpMock.expectOne('/auth/refresh');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    expect(req.request.withCredentials).toBe(true);
    req.flush(authResponse);
    httpMock.expectNone('/auth/refresh');

    await pending;
  });

  it('should resolve once the refresh succeeds, with the session restored in memory', async () => {
    // #given
    const pending = TestBed.runInInjectionContext(() => restoreSessionInitializer());

    // #when
    httpMock.expectOne('/auth/refresh').flush(authResponse);
    await pending;

    // #then
    expect(authService.isLoggedIn()).toBe(true);
    expect(authService.getToken()).toBe('access-token');
  });

  it('should resolve, not reject, when the refresh is rejected — leaving the app in a logged-out state', async () => {
    // #given
    const pending = TestBed.runInInjectionContext(() => restoreSessionInitializer());

    // #when
    httpMock.expectOne('/auth/refresh')
      .flush({error: 'Refresh token invalide ou expiré.'}, {status: 401, statusText: 'Unauthorized'});

    // #then: the promise driving Angular's bootstrap must settle either way,
    // or the whole application would fail to render for every logged-out visitor
    await expect(pending).resolves.toBeUndefined();
    expect(authService.isLoggedIn()).toBe(false);
  });
});
