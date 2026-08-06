import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {of, throwError} from 'rxjs';

import {ResendVerification} from './resend-verification';
import {AuthService} from '../../../../core/auth/services/auth-service';

describe('ResendVerification', () => {
  let component: ResendVerification;
  let fixture: ComponentFixture<ResendVerification>;
  let authServiceMock: { resendVerification: ReturnType<typeof vi.fn> };

  async function setup() {
    authServiceMock = {resendVerification: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [ResendVerification],
      providers: [
        provideRouter([]),
        {provide: AuthService, useValue: authServiceMock},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ResendVerification);
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
    expect(authServiceMock.resendVerification).not.toHaveBeenCalled();
  });

  it('should show the generic confirmation on success', async () => {
    // #given
    await setup();
    authServiceMock.resendVerification.mockReturnValue(of({message: 'ok'}));
    component.form.setValue({email: 'membre@assrone.ch'});

    // #when
    component.onSubmit();

    // #then
    expect(authServiceMock.resendVerification).toHaveBeenCalledWith({email: 'membre@assrone.ch'});
    expect(component.submitted).toBe(true);
  });

  // Anti-enumeration: an already-verified account and an unknown one must
  // both land on the exact same confirmation state.
  it('should show the same generic confirmation on a backend error, never a distinct error state', async () => {
    // #given
    await setup();
    authServiceMock.resendVerification.mockReturnValue(throwError(() => ({status: 429})));
    component.form.setValue({email: 'membre@assrone.ch'});

    // #when
    component.onSubmit();

    // #then
    expect(component.submitted).toBe(true);
    expect(component.isLoading).toBe(false);
  });
});
