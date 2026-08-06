import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {of, throwError} from 'rxjs';

import {ForgotPassword} from './forgot-password';
import {AuthService} from '../../../../core/auth/services/auth-service';

describe('ForgotPassword', () => {
  let component: ForgotPassword;
  let fixture: ComponentFixture<ForgotPassword>;
  let authServiceMock: { forgotPassword: ReturnType<typeof vi.fn> };

  async function setup() {
    authServiceMock = {forgotPassword: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [ForgotPassword],
      providers: [
        provideRouter([]),
        {provide: AuthService, useValue: authServiceMock},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ForgotPassword);
    component = fixture.componentInstance;
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
    component.form.setValue({email: ''});

    // #when
    component.onSubmit();

    // #then
    expect(authServiceMock.forgotPassword).not.toHaveBeenCalled();
  });

  it('should show the generic confirmation on success', async () => {
    // #given
    await setup();
    authServiceMock.forgotPassword.mockReturnValue(of({message: 'ok'}));
    component.form.setValue({email: 'membre@assrone.ch'});

    // #when
    component.onSubmit();

    // #then
    expect(authServiceMock.forgotPassword).toHaveBeenCalledWith({email: 'membre@assrone.ch'});
    expect(component.submitted).toBe(true);
    expect(component.isLoading).toBe(false);
  });

  // Anti-enumeration: an unknown account and a 429 must both land on the
  // exact same confirmation state as a real account — never a distinct error.
  it('should show the same generic confirmation on a backend error, never a distinct error state', async () => {
    // #given
    await setup();
    authServiceMock.forgotPassword.mockReturnValue(throwError(() => ({status: 429})));
    component.form.setValue({email: 'membre@assrone.ch'});

    // #when
    component.onSubmit();

    // #then
    expect(component.submitted).toBe(true);
    expect(component.isLoading).toBe(false);
  });

  it('should not submit twice while a request is already in flight', async () => {
    // #given
    await setup();
    authServiceMock.forgotPassword.mockReturnValue(of({message: 'ok'}));
    component.form.setValue({email: 'membre@assrone.ch'});
    component.isLoading = true;

    // #when
    component.onSubmit();

    // #then
    expect(authServiceMock.forgotPassword).not.toHaveBeenCalled();
  });
});
