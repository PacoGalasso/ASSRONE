import {TestBed} from '@angular/core/testing';
import {HttpClient, provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {JwtInterceptor} from './jwt-interceptor';
import {AuthService} from '../services/auth-service';

describe('JwtInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        provideRouter([]),
        {provide: HTTP_INTERCEPTORS, useClass: JwtInterceptor, multi: true}
      ]
    });
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  /**
   * JwtInterceptor (and, through it, AuthService) is constructed as soon as
   * HttpClient is first injected, because the DI-based interceptor chain
   * reads HTTP_INTERCEPTORS eagerly. AuthService's token signal is seeded
   * from localStorage only at that construction moment, so any test relying
   * on an initial token must populate localStorage before calling this, not
   * after.
   */
  function injectHttp(): void {
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  }

  it('should attach the bearer token to protected requests', () => {
    // #given
    localStorage.setItem('auth_token', 'access-token');
    injectHttp();

    // #when
    http.get('/api/documents').subscribe();

    // #then
    const req = httpMock.expectOne('/api/documents');
    expect(req.request.headers.get('Authorization')).toBe('Bearer access-token');
    req.flush([]);
  });

  it('should not attach a bearer token to public auth endpoints', () => {
    // #given no token needed for login
    injectHttp();

    // #when
    http.post('/auth/generateToken', {}).subscribe();

    // #then
    const req = httpMock.expectOne('/auth/generateToken');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should refresh once and retry the failed request on a single 401', () => {
    // #given
    localStorage.setItem('auth_token', 'expired-token');
    localStorage.setItem('refresh_token', 'refresh-token');
    injectHttp();

    // #when
    http.get('/api/documents').subscribe();

    // #then
    const firstAttempt = httpMock.expectOne('/api/documents');
    firstAttempt.flush({error: 'expired'}, {status: 401, statusText: 'Unauthorized'});

    const refreshReq = httpMock.expectOne('/auth/refresh');
    refreshReq.flush({
      token: 'new-access-token',
      username: 'membre@assrone.ch',
      role: 'ROLE_USER',
      refreshToken: 'new-refresh-token'
    });

    const retry = httpMock.expectOne('/api/documents');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-access-token');
    retry.flush([]);
  });

  it('should send exactly one refresh call and resume all queued requests when multiple 401s arrive concurrently', () => {
    // #given
    localStorage.setItem('auth_token', 'expired-token');
    localStorage.setItem('refresh_token', 'refresh-token');
    injectHttp();

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
      role: 'ROLE_USER',
      refreshToken: 'new-refresh-token'
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
    localStorage.setItem('auth_token', 'expired-token');
    localStorage.setItem('refresh_token', 'expired-refresh-token');
    injectHttp();
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

  it('should also error out queued requests, instead of hanging, when the shared refresh fails', () => {
    // #given
    localStorage.setItem('auth_token', 'expired-token');
    localStorage.setItem('refresh_token', 'expired-refresh-token');
    injectHttp();
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
