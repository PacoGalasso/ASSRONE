import {ComponentFixture, TestBed} from '@angular/core/testing';
import {ActivatedRoute, Router, convertToParamMap} from '@angular/router';
import {of, throwError} from 'rxjs';

import {VerifyEmail} from './verify-email';
import {AuthService} from '../../../../core/auth/services/auth-service';

describe('VerifyEmail', () => {
  let component: VerifyEmail;
  let fixture: ComponentFixture<VerifyEmail>;
  let authServiceMock: { verifyEmail: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  async function setup(token: string | null, verifyResult = of({message: 'ok'})) {
    authServiceMock = {verifyEmail: vi.fn().mockReturnValue(verifyResult)};
    router = {navigate: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [VerifyEmail],
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

    fixture = TestBed.createComponent(VerifyEmail);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should create', async () => {
    // #given / #when
    await setup('un-token-valide');

    // #then
    expect(component).toBeTruthy();
  });

  it('should flag missing-token and never call the backend when no token is in the URL', async () => {
    // #given / #when
    await setup(null);

    // #then
    expect(component.state()).toBe('missing-token');
    expect(authServiceMock.verifyEmail).not.toHaveBeenCalled();
  });

  it('should verify automatically on load and show success', async () => {
    // #given / #when
    await setup('un-token-valide');

    // #then
    expect(authServiceMock.verifyEmail).toHaveBeenCalledWith({token: 'un-token-valide'});
    expect(component.state()).toBe('success');
  });

  it('should scrub the token from the URL after reading it once', async () => {
    // #given / #when
    await setup('un-token-valide');

    // #then
    expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({queryParams: {}, replaceUrl: true}));
  });

  it('should show an invalid state, never a success state, on a token error', async () => {
    // #given / #when
    await setup('token-invalide', throwError(() => ({status: 400})));

    // #then
    expect(component.state()).toBe('invalid');
  });
});
