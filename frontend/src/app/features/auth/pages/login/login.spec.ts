import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ActivatedRoute, Router, convertToParamMap, provideRouter} from '@angular/router';
import {of, throwError} from 'rxjs';

import {Login} from './login';
import {AuthService} from '../../../../core/auth/services/auth-service';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let authServiceMock: { login: ReturnType<typeof vi.fn> };
  let router: Router;

  async function setup(queryParams: Record<string, string> = {}) {
    authServiceMock = {login: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        {provide: AuthService, useValue: authServiceMock},
        {
          provide: ActivatedRoute,
          useValue: {snapshot: {queryParamMap: convertToParamMap(queryParams)}},
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
  }

  it('should create', async () => {
    // #given / #when
    await setup();

    // #then
    expect(component).toBeTruthy();
  });

  it('should not call the backend when the form is invalid', async () => {
    // #given
    await setup();
    component.loginForm.setValue({email: '', password: ''});

    // #when
    component.onSubmit();

    // #then
    expect(authServiceMock.login).not.toHaveBeenCalled();
  });

  it('should redirect to /dashboard after a successful login', async () => {
    // #given
    await setup();
    authServiceMock.login.mockReturnValue(of({
      token: 'access-token',
      username: 'membre@assrone.ch',
      role: 'ROLE_USER'
    }));
    const navigateSpy = vi.spyOn(router, 'navigate');
    component.loginForm.setValue({email: 'membre@assrone.ch', password: 'motdepasse123'});

    // #when
    component.onSubmit();

    // #then
    expect(authServiceMock.login).toHaveBeenCalledWith({email: 'membre@assrone.ch', password: 'motdepasse123'});
    expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
    expect(component.isLoading).toBe(false);
  });

  it('should surface the backend error message and stop loading on failure', async () => {
    // #given
    await setup();
    authServiceMock.login.mockReturnValue(
      throwError(() => ({error: {error: 'Email ou mot de passe incorrect.'}}))
    );
    component.loginForm.setValue({email: 'membre@assrone.ch', password: 'mauvais-mot-de-passe'});

    // #when
    component.onSubmit();

    // #then
    expect(component.errorMessage).toBe('Email ou mot de passe incorrect.');
    expect(component.isLoading).toBe(false);
  });

  it('should show the post-reset success message when arriving with passwordReset=1', async () => {
    // #given / #when
    await setup({passwordReset: '1'});

    // #then
    expect(component.passwordResetSuccess).toBe(true);
  });

  it('should not show the post-reset success message on a normal visit', async () => {
    // #given / #when
    await setup();

    // #then
    expect(component.passwordResetSuccess).toBe(false);
  });

  it('should show a precise message and offer resending verification on a 403 EMAIL_NOT_VERIFIED', async () => {
    // #given
    await setup();
    authServiceMock.login.mockReturnValue(
      throwError(() => ({status: 403, error: {error: 'Veuillez vérifier votre adresse email avant de vous connecter.'}}))
    );
    component.loginForm.setValue({email: 'membre@assrone.ch', password: 'motdepasse123'});

    // #when
    component.onSubmit();

    // #then
    expect(component.emailNotVerified).toBe(true);
    expect(component.errorMessage).toBe('Veuillez vérifier votre adresse email avant de vous connecter.');
  });

  it('should not flag emailNotVerified on a plain 401 bad-credentials error', async () => {
    // #given
    await setup();
    authServiceMock.login.mockReturnValue(
      throwError(() => ({status: 401, error: {error: 'Email ou mot de passe incorrect.'}}))
    );
    component.loginForm.setValue({email: 'membre@assrone.ch', password: 'mauvais-mot-de-passe'});

    // #when
    component.onSubmit();

    // #then
    expect(component.emailNotVerified).toBe(false);
    expect(component.errorMessage).toBe('Email ou mot de passe incorrect.');
  });
});
