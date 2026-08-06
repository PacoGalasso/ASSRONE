import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ActivatedRoute, Router, convertToParamMap} from '@angular/router';
import {of, throwError} from 'rxjs';

import {ResetPassword} from './reset-password';
import {AuthService} from '../../../../core/auth/services/auth-service';

describe('ResetPassword', () => {
  let component: ResetPassword;
  let fixture: ComponentFixture<ResetPassword>;
  let authServiceMock: { resetPassword: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  async function setup(token: string | null = 'un-token-valide') {
    authServiceMock = {resetPassword: vi.fn()};
    router = {navigate: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [ResetPassword],
      providers: [
        {provide: AuthService, useValue: authServiceMock},
        {provide: Router, useValue: router},
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {queryParamMap: convertToParamMap(token ? {token} : {})},
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ResetPassword);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should create', async () => {
    // #given / #when
    await setup();

    // #then
    expect(component).toBeTruthy();
  });

  it('should flag the token as missing and never render the form when no token is in the URL', async () => {
    // #given / #when
    await setup(null);

    // #then
    expect(component.tokenMissing).toBe(true);
  });

  it('should scrub the token from the URL after reading it once', async () => {
    // #given / #when
    await setup('un-token-valide');

    // #then
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({queryParams: {}, replaceUrl: true}));
  });

  it('should not call the backend when the form is invalid', async () => {
    // #given
    await setup();
    component.form.setValue({newPassword: '', confirmPassword: ''});

    // #when
    component.onSubmit();

    // #then
    expect(authServiceMock.resetPassword).not.toHaveBeenCalled();
  });

  it('should not call the backend when the passwords do not match', async () => {
    // #given
    await setup();
    component.form.setValue({newPassword: 'nouveauMotDePasse123', confirmPassword: 'different123'});

    // #when
    component.onSubmit();

    // #then
    expect(component.form.errors?.['passwordsMismatch']).toBeTruthy();
    expect(authServiceMock.resetPassword).not.toHaveBeenCalled();
  });

  it('should reset the password and redirect to login without auto-login on success', async () => {
    // #given
    await setup();
    authServiceMock.resetPassword.mockReturnValue(of({message: 'ok'}));
    component.form.setValue({newPassword: 'nouveauMotDePasse123', confirmPassword: 'nouveauMotDePasse123'});

    // #when
    component.onSubmit();

    // #then
    expect(authServiceMock.resetPassword).toHaveBeenCalledWith({
      token: 'un-token-valide',
      newPassword: 'nouveauMotDePasse123',
    });
    expect(router.navigate).toHaveBeenCalledWith(['/login'], {queryParams: {passwordReset: '1'}});
  });

  it('should show an inline error, without navigating away, on an invalid or expired token', async () => {
    // #given
    await setup();
    authServiceMock.resetPassword.mockReturnValue(throwError(() => ({status: 400})));
    component.form.setValue({newPassword: 'nouveauMotDePasse123', confirmPassword: 'nouveauMotDePasse123'});
    router.navigate.mockClear();

    // #when
    component.onSubmit();

    // #then
    expect(component.errorMessage).toBeTruthy();
    expect(router.navigate).not.toHaveBeenCalled();
    expect(component.isLoading).toBe(false);
  });
});
