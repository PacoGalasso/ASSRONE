import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router} from '@angular/router';
import {AuthService} from './auth-service';
import {AuthResponse, RegisterResponse} from '../models/auth.models';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: Router;

  const authResponse: AuthResponse = {
    token: 'access-token',
    username: 'membre@assrone.ch',
    role: 'ROLE_USER',
    refreshToken: 'refresh-token'
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  describe('login', () => {
    it('should POST credentials and store both tokens on success', () => {
      // #given
      const credentials = {email: 'membre@assrone.ch', password: 'motdepasse123'};

      // #when
      service.login(credentials).subscribe();

      // #then
      const req = httpMock.expectOne('/auth/generateToken');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(credentials);
      req.flush(authResponse);

      expect(service.getToken()).toBe('access-token');
      expect(localStorage.getItem('refresh_token')).toBe('refresh-token');
      expect(service.isLoggedIn()).toBe(true);
    });
  });

  describe('register', () => {
    it('should POST the registration payload and NOT authenticate the user', () => {
      // #given
      const request = {
        username: 'jdupont',
        email: 'jean.dupont@assrone.ch',
        firstName: 'Jean',
        lastName: 'Dupont',
        password: 'motdepasse123'
      };
      const response: RegisterResponse = {
        id: 1,
        username: 'jdupont',
        email: 'jean.dupont@assrone.ch',
        firstName: 'Jean',
        lastName: 'Dupont'
      };

      // #when
      service.register(request).subscribe();

      // #then
      const req = httpMock.expectOne('/auth/addNewUser');
      expect(req.request.method).toBe('POST');
      req.flush(response);

      expect(service.isLoggedIn()).toBe(false);
      expect(service.getToken()).toBeNull();
      expect(localStorage.getItem('auth_token')).toBeNull();
    });
  });

  describe('refreshToken', () => {
    it('should error without calling the backend when no refresh token is stored', async () => {
      // #given no refresh token in storage

      // #when
      const errorReceived = new Promise<Error>((resolve) => {
        service.refreshToken().subscribe({error: (err) => resolve(err)});
      });

      // #then
      const err = await errorReceived;
      expect(err.message).toBe('No refresh token available');
      httpMock.expectNone('/auth/refresh');
    });

    it('should rotate tokens and update storage on success', () => {
      // #given
      localStorage.setItem('refresh_token', 'old-refresh-token');
      const rotated: AuthResponse = {
        token: 'new-access-token',
        username: 'membre@assrone.ch',
        role: 'ROLE_USER',
        refreshToken: 'new-refresh-token'
      };

      // #when
      service.refreshToken().subscribe();

      // #then
      const req = httpMock.expectOne('/auth/refresh');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({refreshToken: 'old-refresh-token'});
      req.flush(rotated);

      expect(service.getToken()).toBe('new-access-token');
      expect(localStorage.getItem('refresh_token')).toBe('new-refresh-token');
    });

    it('should propagate a backend refresh failure without altering stored auth state', () => {
      // #given
      localStorage.setItem('refresh_token', 'expired-refresh-token');
      localStorage.setItem('auth_token', 'still-there');
      let receivedError: unknown;

      // #when
      service.refreshToken().subscribe({error: (err) => receivedError = err});

      // #then
      const req = httpMock.expectOne('/auth/refresh');
      req.flush({error: 'Refresh token invalide ou expiré.'}, {status: 401, statusText: 'Unauthorized'});

      expect(receivedError).toBeTruthy();
      expect(localStorage.getItem('auth_token')).toBe('still-there');
    });
  });

  describe('logout', () => {
    it('should revoke the refresh token on the backend, clear storage and redirect home', () => {
      // #given
      localStorage.setItem('auth_token', 'access-token');
      localStorage.setItem('refresh_token', 'refresh-token');
      localStorage.setItem('auth_user', JSON.stringify({username: 'membre@assrone.ch', role: 'ROLE_USER'}));
      const navigateSpy = vi.spyOn(router, 'navigate');

      // #when
      service.logout();

      // #then
      const req = httpMock.expectOne('/auth/logout');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({refreshToken: 'refresh-token'});
      req.flush(null);

      expect(localStorage.getItem('auth_token')).toBeNull();
      expect(localStorage.getItem('refresh_token')).toBeNull();
      expect(localStorage.getItem('auth_user')).toBeNull();
      expect(navigateSpy).toHaveBeenCalledWith(['/']);
    });

    it('should not call the backend when no refresh token is stored, but still clear local state', () => {
      // #given no refresh token in storage
      localStorage.setItem('auth_token', 'access-token');

      // #when
      service.logout();

      // #then
      httpMock.expectNone('/auth/logout');
      expect(localStorage.getItem('auth_token')).toBeNull();
    });

    it('should still clear local state even if the backend call fails', () => {
      // #given
      localStorage.setItem('refresh_token', 'refresh-token');

      // #when
      service.logout();

      // #then
      const req = httpMock.expectOne('/auth/logout');
      req.flush({error: 'boom'}, {status: 500, statusText: 'Internal Server Error'});

      expect(localStorage.getItem('refresh_token')).toBeNull();
      expect(service.isLoggedIn()).toBe(false);
    });
  });

  describe('isAdmin', () => {
    it('should return true only when the stored role is ROLE_ADMIN', () => {
      // #given
      service.login({email: 'admin@assrone.ch', password: 'motdepasse123'}).subscribe();
      httpMock.expectOne('/auth/generateToken').flush({...authResponse, role: 'ROLE_ADMIN'});

      // #when / #then
      expect(service.isAdmin()).toBe(true);
    });

    it('should return false when logged out', () => {
      // #given no auth performed

      // #when / #then
      expect(service.isAdmin()).toBe(false);
    });
  });
});
