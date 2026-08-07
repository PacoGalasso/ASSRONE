import {TestBed} from '@angular/core/testing';
import {RouterTestingHarness} from '@angular/router/testing';
import {provideRouter} from '@angular/router';
import {provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';

import {routes} from './app.routes';
import {ForgotPassword} from './features/auth/pages/forgot-password/forgot-password';
import {ResetPassword} from './features/auth/pages/reset-password/reset-password';
import {VerifyEmail} from './features/auth/pages/verify-email/verify-email';
import {Login} from './features/auth/pages/login/login';
import {JwtInterceptor} from './core/auth/interceptors/jwt-interceptor';

// Proves the actual bug reported after manual Docker/Mailpit validation:
// these routes must never live under /auth/** — that prefix is proxied
// straight to the backend (proxy.conf.json in dev, nginx "location /auth/"
// in production), so a browser GET there never reaches Angular's router at
// all and surfaces as a raw backend 401 instead of the SPA page. These tests
// exercise the real `routes` array from app.routes.ts, not a stub.
describe('Account-lifecycle routes', () => {
  async function harness() {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        {provide: HTTP_INTERCEPTORS, useClass: JwtInterceptor, multi: true},
      ],
    });
    return RouterTestingHarness.create();
  }

  it('should render ForgotPassword when navigating to /forgot-password', async () => {
    // #given
    const routerHarness = await harness();

    // #when
    const component = await routerHarness.navigateByUrl('/forgot-password');

    // #then
    expect(component).toBeInstanceOf(ForgotPassword);
  });

  it('should render ResetPassword on a direct navigation to /reset-password?token=abc', async () => {
    // #given
    const routerHarness = await harness();

    // #when
    const component = await routerHarness.navigateByUrl('/reset-password?token=abc');

    // #then
    expect(component).toBeInstanceOf(ResetPassword);
  });

  it('should never issue a backend request merely by navigating to /reset-password?token=abc', async () => {
    // #given
    const routerHarness = await harness();

    // #when
    await routerHarness.navigateByUrl('/reset-password?token=abc');

    // #then: client-side routing alone must never touch the network — no
    // GET (or any) call to /auth/reset-password, and critically no implicit
    // /auth/refresh either (see JwtInterceptor).
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.verify();
  });

  it('should POST /auth/reset-password only once the user actually submits the form', async () => {
    // #given
    const routerHarness = await harness();
    const component = await routerHarness.navigateByUrl('/reset-password?token=abc') as ResetPassword;
    const httpMock = TestBed.inject(HttpTestingController);
    component.form.setValue({newPassword: 'nouveauMotDePasse123', confirmPassword: 'nouveauMotDePasse123'});

    // #when
    component.onSubmit();

    // #then
    const req = httpMock.expectOne('/auth/reset-password');
    expect(req.request.method).toBe('POST');
    req.flush({message: 'ok'});
  });

  it('should render VerifyEmail on a direct navigation to /verify-email?token=abc', async () => {
    // #given
    const routerHarness = await harness();

    // #when
    const component = await routerHarness.navigateByUrl('/verify-email?token=abc');

    // #then
    expect(component).toBeInstanceOf(VerifyEmail);
  });

  it('should render Login when navigating to /login', async () => {
    // #given
    const routerHarness = await harness();

    // #when
    const component = await routerHarness.navigateByUrl('/login');

    // #then
    expect(component).toBeInstanceOf(Login);
  });

  it('should define no account-lifecycle page under the /auth/** namespace, which is reserved for the backend API proxy', () => {
    // #given / #when
    const topLevelPaths = routes.map(route => route.path);

    // #then
    expect(topLevelPaths).not.toContain('auth');
    expect(topLevelPaths).toEqual(expect.arrayContaining([
      'login', 'forgot-password', 'reset-password', 'verify-email', 'resend-verification',
    ]));
  });
});
