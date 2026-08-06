import {TestBed} from '@angular/core/testing';
import {HttpClient, provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {JwtInterceptor} from './jwt-interceptor';
import {AuthService} from '../services/auth-service';
import {AuthResponse} from '../models/auth.models';

describe('JwtInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  const authResponse: AuthResponse = {
    token: 'access-token',
    username: 'membre@assrone.ch',
    role: 'ROLE_USER'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        provideRouter([]),
        {provide: HTTP_INTERCEPTORS, useClass: JwtInterceptor, multi: true}
      ]
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  /**
   * Seeds an in-memory access token the same way the app ever legitimately
   * gets one — through a real login() call — since AuthService no longer
   * reads anything from storage at construction time.
   */
  function loginWith(token: string): void {
    authService.login({email: 'membre@assrone.ch', password: 'motdepasse123'}).subscribe();
    httpMock.expectOne('/auth/generateToken').flush({...authResponse, token});
  }

  it('should attach the bearer token to protected requests', () => {
    // #given
    loginWith('access-token');

    // #when
    http.get('/api/documents').subscribe();

    // #then
    const req = httpMock.expectOne('/api/documents');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-token');
    req.flush([]);
  });

  it('should not attach a bearer token, and never send the literal string "undefined", when no token is in memory yet', () => {
    // #given no login performed — as is always the case before app initializer restoration

    // #when
    http.get('/api/documents').subscribe();

    // #then
    const req = httpMock.expectOne('/api/documents');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('should not attach a bearer token to /auth/generateToken', () => {
    // #given no token needed for login

    // #when
    http.post('/auth/generateToken', {}).subscribe();

    // #then
    const req = httpMock.expectOne('/auth/generateToken');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should not attach a bearer token to /auth/refresh and must not intercept its own 401', () => {
    // #given
    loginWith('access-token');

    // #when
    authService.refreshToken().subscribe({error: () => undefined});

    // #then: exactly one request, no recursive refresh-of-a-refresh
    const req = httpMock.expectOne('/auth/refresh');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({error: 'expired'}, {status: 401, statusText: 'Unauthorized'});
    httpMock.expectNone('/auth/refresh');
  });

  it('should not attach a bearer token to /auth/logout', () => {
    // #given
    loginWith('access-token');

    // #when
    authService.logout();

    // #then
    const req = httpMock.expectOne('/auth/logout');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(null);
  });

  it('should refresh once and retry the failed request on a single 401', () => {
    // #given
    loginWith('expired-token');

    // #when
    http.get('/api/documents').subscribe();

    // #then
    const firstAttempt = httpMock.expectOne('/api/documents');
    firstAttempt.flush({error: 'expired'}, {status: 401, statusText: 'Unauthorized'});

    const refreshReq = httpMock.expectOne('/auth/refresh');
    expect(refreshReq.request.withCredentials).toBe(true);
    expect(refreshReq.request.body).toBeNull();
    refreshReq.flush({
      token: 'new-access-token',
      username: 'membre@assrone.ch',
      role: 'ROLE_USER'
    });

    const retry = httpMock.expectOne('/api/documents');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-access-token');
    retry.flush([]);
  });

  it('should send exactly one refresh call and resume all queued requests when multiple 401s arrive concurrently', () => {
    // #given
    loginWith('expired-token');

    // #when
    http.get('/api/documents').subscribe();
    http.get('/api/events').subscribe();

    // #then
    const firstDocuments = httpMock.expectOne('/api/documents');
    const firstEvents = httpMock.expectOne('/api/events');
    firstDocuments.flush({error: 'expired'}, {status: 401, statusText: 'Unauthorized'});
    firstEvents.flush({error: 'expired'}, {status: 401, statusText: 'Unauthorized'});

    httpMock.expectOne('/auth/refresh').flush({
      token: 'new-access-token',
      username: 'membre@assrone.ch',
      role: 'ROLE_USER'
    });

    const retriedDocuments = httpMock.expectOne('/api/documents');
    const retriedEvents = httpMock.expectOne('/api/events');
    expect(retriedDocuments.request.headers.get('Authorization')).toBe('Bearer new-access-token');
    expect(retriedEvents.request.headers.get('Authorization')).toBe('Bearer new-access-token');
    retriedDocuments.flush([]);
    retriedEvents.flush([]);
  });

  it('should log out and propagate the error, without looping, when the refresh call itself fails', () => {
    // #given
    loginWith('expired-token');
    const logoutSpy = vi.spyOn(authService, 'logout').mockImplementation(() => {});
    let receivedError: unknown;

    // #when
    http.get('/api/documents').subscribe({error: (err) => receivedError = err});

    // #then
    httpMock.expectOne('/api/documents')
      .flush({error: 'expired'}, {status: 401, statusText: 'Unauthorized'});
    httpMock.expectOne('/auth/refresh')
      .flush({error: 'Refresh token invalide ou expiré.'}, {status: 401, statusText: 'Unauthorized'});

    expect(logoutSpy).toHaveBeenCalledTimes(1);
    expect(receivedError).toBeTruthy();
    httpMock.expectNone('/auth/refresh');
  });

  it.each([404, 410, 500])(
    'should never attempt a refresh for a %s error — it stays local to the failing request',
    (status) => {
      // #given
      loginWith('access-token');
      const logoutSpy = vi.spyOn(authService, 'logout').mockImplementation(() => {});
      let receivedError: unknown;

      // #when: representative of a failed avatar fetch (GET /api/profile/avatar)
      http.get('/api/profile/avatar').subscribe({error: (err) => receivedError = err});

      // #then
      httpMock.expectOne('/api/profile/avatar')
        .flush(null, {status, statusText: 'Error'});

      expect(receivedError).toBeTruthy();
      expect(logoutSpy).not.toHaveBeenCalled();
      httpMock.expectNone('/auth/refresh');
    }
  );

  it('should never attempt a refresh for a network error — it stays local to the failing request', () => {
    // #given
    loginWith('access-token');
    const logoutSpy = vi.spyOn(authService, 'logout').mockImplementation(() => {});
    let receivedError: unknown;

    // #when
    http.get('/api/profile/avatar').subscribe({error: (err) => receivedError = err});

    // #then
    httpMock.expectOne('/api/profile/avatar')
      .error(new ProgressEvent('error'));

    expect(receivedError).toBeTruthy();
    expect(logoutSpy).not.toHaveBeenCalled();
    httpMock.expectNone('/auth/refresh');
  });

  it('should also error out queued requests, instead of hanging, when the shared refresh fails', () => {
    // #given
    loginWith('expired-token');
    vi.spyOn(authService, 'logout').mockImplementation(() => {});
    let firstError: unknown;
    let secondError: unknown;

    // #when
    http.get('/api/documents').subscribe({error: (err) => firstError = err});
    http.get('/api/events').subscribe({error: (err) => secondError = err});

    // #then
    httpMock.expectOne('/api/documents').flush({error: 'expired'}, {status: 401, statusText: 'Unauthorized'});
    httpMock.expectOne('/api/events').flush({error: 'expired'}, {status: 401, statusText: 'Unauthorized'});
    httpMock.expectOne('/auth/refresh')
      .flush({error: 'Refresh token invalide ou expiré.'}, {status: 401, statusText: 'Unauthorized'});

    expect(firstError).toBeTruthy();
    expect(secondError).toBeTruthy();
    httpMock.expectNone('/api/documents');
    httpMock.expectNone('/api/events');
  });
});
