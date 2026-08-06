import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {signal} from '@angular/core';
import {of, throwError} from 'rxjs';
import {HttpErrorResponse} from '@angular/common/http';
import {Navbar} from './navbar';
import {AuthService} from '../../../core/auth/services/auth-service';
import {ProfileService} from '../../../core/auth/services/profile-service';

describe('Navbar', () => {
  let fixture: ComponentFixture<Navbar>;
  let authService: {
    isLoggedIn: ReturnType<typeof signal<boolean>>;
    user: ReturnType<typeof signal<{ username: string; role: string; email: string } | null>>;
    isAdmin: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };
  let profileService: { getAvatar: ReturnType<typeof vi.fn> };

  async function setUp(avatarResult: 'success' | HttpErrorResponse): Promise<void> {
    authService = {
      isLoggedIn: signal(true),
      user: signal({username: 'membre', role: 'ROLE_USER', email: 'membre@assrone.ch'}),
      isAdmin: vi.fn().mockReturnValue(false),
      logout: vi.fn(),
    };
    profileService = {
      getAvatar: vi.fn().mockReturnValue(
        avatarResult === 'success' ? of(new Blob(['fake-image'])) : throwError(() => avatarResult)
      ),
    };

    await TestBed.configureTestingModule({
      imports: [Navbar],
      providers: [
        provideRouter([]),
        {provide: AuthService, useValue: authService},
        {provide: ProfileService, useValue: profileService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Navbar);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  // #given/#when a login succeeds but the avatar endpoint fails (404 — missing
  // file on disk, 410, 500, or a network error) #then the failure must stay
  // local to the avatar: no logout, no session-clearing call of any kind.
  describe.each([
    ['404 (avatar reference missing on disk)', new HttpErrorResponse({status: 404})],
    ['410', new HttpErrorResponse({status: 410})],
    ['500', new HttpErrorResponse({status: 500})],
    ['a network error (status 0)', new HttpErrorResponse({status: 0})],
  ])('when the avatar request fails with %s', (_label, error) => {
    it('leaves the user logged in and shows the default avatar (no image URL)', async () => {
      // #given/#when
      await setUp(error);

      // #then
      expect(authService.isLoggedIn()).toBe(true);
      expect(fixture.componentInstance.avatarUrl()).toBeNull();
    });

    it('never calls AuthService#logout', async () => {
      // #given/#when
      await setUp(error);

      // #then
      expect(authService.logout).not.toHaveBeenCalled();
    });
  });

  it('shows the avatar image when the avatar request succeeds', async () => {
    // #given/#when
    await setUp('success');

    // #then
    expect(fixture.componentInstance.avatarUrl()).not.toBeNull();
    expect(authService.logout).not.toHaveBeenCalled();
  });
});
