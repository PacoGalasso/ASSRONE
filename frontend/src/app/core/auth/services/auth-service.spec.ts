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
    role: 'ROLE_USER'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('storage isolation', () => {
    it('should never touch localStorage for the access token', () => {
      // #given
      const credentials = {email: 'membre@assrone.ch', password: 'motdepasse123'};

      // #when
      service.login(credentials).subscribe();
      httpMock.expectOne('/auth/generateToken').flush(authResponse);

      // #then
      expect(localStorage.getItem('auth_token')).toBeNull();
      expect(localStorage.length).toBe(0);
    });

    it('should never touch sessionStorage for the access token', () => {
      // #given
      const credentials = {email: 'membre@assrone.ch', password: 'motdepasse123'};

      // #when
      service.login(credentials).subscribe();
      httpMock.expectOne('/auth/generateToken').flush(authResponse);

      // #then
      expect(sessionStorage.getItem('auth_token')).toBeNull();
    });
  });

  describe('login', () => {
    it('should POST credentials with credentials included and place the token only in memory', () => {
      // #given
      const credentials = {email: 'membre@assrone.ch', password: 'motdepasse123'};

      // #when
      service.login(credentials).subscribe();

      // #then
      const req = httpMock.expectOne('/auth/generateToken');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(credentials);
      expect(req.request.withCredentials).toBe(true);
      req.flush(authResponse);

      expect(service.getToken()).toBe('access-token');
      expect(service.isLoggedIn()).toBe(true);
      expect(localStorage.getItem('auth_token')).toBeNull();
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
    });
  });

  describe('forgotPassword', () => {
    it('should POST the email and never touch auth state', () => {
      // #given
      const request = {email: 'membre@assrone.ch'};

      // #when
      service.forgotPassword(request).subscribe();

      // #then
      const req = httpMock.expectOne('/auth/forgot-password');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush({message: 'Si un compte correspond à cette adresse, un email a été envoyé.'});

      expect(service.isLoggedIn()).toBe(false);
      expect(service.getToken()).toBeNull();
    });
  });

  describe('resetPassword', () => {
    it('should POST the token and new password and never authenticate the user', () => {
      // #given
      const request = {token: 'un-token', newPassword: 'nouveauMotDePasse123'};

      // #when
      service.resetPassword(request).subscribe();

      // #then
      const req = httpMock.expectOne('/auth/reset-password');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush({message: 'Votre mot de passe a été réinitialisé.'});

      expect(service.isLoggedIn()).toBe(false);
      expect(service.getToken()).toBeNull();
    });
  });

  describe('verifyEmail', () => {
    it('should POST the token', () => {
      // #given
      const request = {token: 'un-token-de-verification'};

      // #when
      service.verifyEmail(request).subscribe();

      // #then
      const req = httpMock.expectOne('/auth/verify-email');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush({message: 'Votre adresse email a été vérifiée.'});
    });
  });

  describe('resendVerification', () => {
    it('should POST the email', () => {
      // #given
      const request = {email: 'membre@assrone.ch'};

      // #when
      service.resendVerification(request).subscribe();

      // #then
      const req = httpMock.expectOne('/auth/resend-verification');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush({message: 'Un nouvel email a été envoyé.'});
    });
  });

  describe('refreshToken', () => {
    it('should POST with credentials included and no token in the request body', () => {
      // #given
      const rotated: AuthResponse = {
        token: 'new-access-token',
        username: 'membre@assrone.ch',
        role: 'ROLE_USER'
      };

      // #when
      service.refreshToken().subscribe();

      // #then
      const req = httpMock.expectOne('/auth/refresh');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toBeNull();
      expect(req.request.withCredentials).toBe(true);
      req.flush(rotated);

      expect(service.getToken()).toBe('new-access-token');
    });

    it('should propagate a backend refresh failure without altering in-memory state', () => {
      // #given
      let receivedError: unknown;

      // #when
      service.refreshToken().subscribe({error: (err) => receivedError = err});

      // #then
      const req = httpMock.expectOne('/auth/refresh');
      req.flush({error: 'Refresh token invalide ou expiré.'}, {status: 401, statusText: 'Unauthorized'});

      expect(receivedError).toBeTruthy();
      expect(service.isLoggedIn()).toBe(false);
    });
  });

  describe('restoreSession', () => {
    it('should place the token and role in memory when the initial refresh succeeds', () => {
      // #given
      const admin: AuthResponse = {token: 'restored-token', username: 'admin@assrone.ch', role: 'ROLE_ADMIN'};

      // #when
      service.restoreSession().subscribe();

      // #then
      httpMock.expectOne('/auth/refresh').flush(admin);

      expect(service.isLoggedIn()).toBe(true);
      expect(service.getToken()).toBe('restored-token');
      expect(service.isAdmin()).toBe(true);
    });

    it('should restore a ROLE_USER identity correctly', () => {
      // #given
      const member: AuthResponse = {token: 'restored-token', username: 'membre@assrone.ch', role: 'ROLE_USER'};

      // #when
      service.restoreSession().subscribe();

      // #then
      httpMock.expectOne('/auth/refresh').flush(member);

      expect(service.isLoggedIn()).toBe(true);
      expect(service.isAdmin()).toBe(false);
      expect(service.user()?.role).toBe('ROLE_USER');
    });

    it('should resolve to a logged-out state, without throwing, when the initial refresh is rejected', () => {
      // #given
      let completed = false;
      let errored = false;

      // #when
      service.restoreSession().subscribe({
        next: () => completed = true,
        error: () => errored = true
      });

      // #then
      httpMock.expectOne('/auth/refresh')
        .flush({error: 'Refresh token invalide ou expiré.'}, {status: 401, statusText: 'Unauthorized'});

      expect(errored).toBe(false);
      expect(completed).toBe(true);
      expect(service.isLoggedIn()).toBe(false);
      expect(service.getToken()).toBeNull();
    });
  });

  describe('logout', () => {
    it('should call the backend with credentials included, clear in-memory state and redirect home', () => {
      // #given
      service.login({email: 'membre@assrone.ch', password: 'motdepasse123'}).subscribe();
      httpMock.expectOne('/auth/generateToken').flush(authResponse);
      const navigateSpy = vi.spyOn(router, 'navigate');

      // #when
      service.logout();

      // #then
      const req = httpMock.expectOne('/auth/logout');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toBeNull();
      expect(req.request.withCredentials).toBe(true);
      req.flush(null);

      expect(service.getToken()).toBeNull();
      expect(service.user()).toBeNull();
      expect(navigateSpy).toHaveBeenCalledWith(['/']);
    });

    it('should clear in-memory state and redirect home even when the backend call fails', () => {
      // #given
      service.login({email: 'membre@assrone.ch', password: 'motdepasse123'}).subscribe();
      httpMock.expectOne('/auth/generateToken').flush(authResponse);
      const navigateSpy = vi.spyOn(router, 'navigate');

      // #when
      service.logout();

      // #then
      const req = httpMock.expectOne('/auth/logout');
      req.flush({error: 'boom'}, {status: 500, statusText: 'Internal Server Error'});

      expect(service.isLoggedIn()).toBe(false);
      expect(service.getToken()).toBeNull();
      expect(navigateSpy).toHaveBeenCalledWith(['/']);
    });
  });

  describe('endLocalSession', () => {
    it('should clear in-memory state, redirect home and never call the backend', () => {
      // #given
      service.login({email: 'membre@assrone.ch', password: 'motdepasse123'}).subscribe();
      httpMock.expectOne('/auth/generateToken').flush(authResponse);
      const navigateSpy = vi.spyOn(router, 'navigate');

      // #when
      service.endLocalSession();

      // #then
      expect(service.getToken()).toBeNull();
      expect(service.user()).toBeNull();
      expect(navigateSpy).toHaveBeenCalledWith(['/']);
    });

    it('should broadcast logout to other tabs without including a token value', async () => {
      // #given
      service.login({email: 'membre@assrone.ch', password: 'motdepasse123'}).subscribe();
      httpMock.expectOne('/auth/generateToken').flush(authResponse);
      const receiverChannel = new BroadcastChannel('assrone-auth');
      const received: unknown[] = [];
      receiverChannel.onmessage = (event) => received.push(event.data);

      try {
        // #when
        service.endLocalSession();

        // #then
        await vi.waitFor(() => expect(received).toEqual(['logout']));
        expect(JSON.stringify(received)).not.toContain('access-token');
      } finally {
        receiverChannel.close();
      }
    });
  });

  describe('isAdmin', () => {
    it('should return true only when the in-memory role is ROLE_ADMIN', () => {
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

  describe('cross-tab logout sync', () => {
    it('should clear this tab\'s in-memory state, without navigating, when another tab broadcasts logout', async () => {
      // #given: this tab is logged in, as if restored independently in its own memory
      service.login({email: 'membre@assrone.ch', password: 'motdepasse123'}).subscribe();
      httpMock.expectOne('/auth/generateToken').flush(authResponse);
      expect(service.isLoggedIn()).toBe(true);
      const navigateSpy = vi.spyOn(router, 'navigate');
      const senderChannel = new BroadcastChannel('assrone-auth');

      try {
        // #when: another tab's AuthService instance posts the logout message
        senderChannel.postMessage('logout');

        // #then
        await vi.waitFor(() => expect(service.isLoggedIn()).toBe(false));
        expect(service.getToken()).toBeNull();
        expect(navigateSpy).not.toHaveBeenCalled();
      } finally {
        senderChannel.close();
      }
    });

    it('should never include a token value in the broadcast message', async () => {
      // #given
      service.login({email: 'membre@assrone.ch', password: 'motdepasse123'}).subscribe();
      httpMock.expectOne('/auth/generateToken').flush(authResponse);
      const receiverChannel = new BroadcastChannel('assrone-auth');
      const received: unknown[] = [];
      receiverChannel.onmessage = (event) => received.push(event.data);

      try {
        // #when
        service.logout();
        httpMock.expectOne('/auth/logout').flush(null);

        // #then
        await vi.waitFor(() => expect(received).toEqual(['logout']));
        expect(JSON.stringify(received)).not.toContain('access-token');
      } finally {
        receiverChannel.close();
      }
    });
  });
});
