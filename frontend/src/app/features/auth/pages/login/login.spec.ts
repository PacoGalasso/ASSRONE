import {ComponentFixture, TestBed} from '@angular/core/testing';
import {Router, provideRouter} from '@angular/router';
import {of, throwError} from 'rxjs';

import {Login} from './login';
import {AuthService} from '../../../../core/auth/services/auth-service';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let authServiceMock: { login: ReturnType<typeof vi.fn> };
  let router: Router;

  async function setup() {
    authServiceMock = {login: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        {provide: AuthService, useValue: authServiceMock},
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
      role: 'ROLE_USER',
      refreshToken: 'refresh-token'
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
});
