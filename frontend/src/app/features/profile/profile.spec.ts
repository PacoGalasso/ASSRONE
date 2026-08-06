import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { Profile } from './profile';
import { ProfileService } from '../../core/auth/services/profile-service';
import { AuthService } from '../../core/auth/services/auth-service';
import { UserProfile } from '../../core/auth/models/profile.model';

const PROFILE: UserProfile = {
  id: 1,
  email: 'membre@assrone.ch',
  username: 'membre',
  firstName: 'Membre',
  lastName: 'Test',
  role: 'ROLE_USER',
  createdAt: '2026-01-01T10:00:00',
};

describe('Profile', () => {
  let fixture: ComponentFixture<Profile>;
  let profileService: {
    getProfile: ReturnType<typeof vi.fn>;
    getAvatar: ReturnType<typeof vi.fn>;
    updateProfile: ReturnType<typeof vi.fn>;
    changePassword: ReturnType<typeof vi.fn>;
  };
  let authService: { endLocalSession: ReturnType<typeof vi.fn> };

  async function setUp(avatarResult: 'success' | HttpErrorResponse): Promise<void> {
    profileService = {
      getProfile: vi.fn().mockReturnValue(of(PROFILE)),
      getAvatar: vi.fn().mockReturnValue(
        avatarResult === 'success' ? of(new Blob(['fake-image'])) : throwError(() => avatarResult)
      ),
      updateProfile: vi.fn(),
      changePassword: vi.fn(),
    };
    authService = {endLocalSession: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [Profile],
      providers: [
        {provide: ProfileService, useValue: profileService},
        {provide: AuthService, useValue: authService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Profile);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('should create', async () => {
    // #given / #when
    await setUp('success');

    // #then
    expect(fixture.componentInstance).toBeTruthy();
  });

  describe.each([
    ['404 (avatar reference missing on disk)', new HttpErrorResponse({status: 404})],
    ['500', new HttpErrorResponse({status: 500})],
  ])('when the avatar request fails with %s', (_label, error) => {
    it('shows the default avatar (no image URL) without throwing, and the rest of the profile still loads', async () => {
      // #given/#when
      await setUp(error);

      // #then
      expect(fixture.componentInstance.avatarUrl()).toBeNull();
      expect(fixture.componentInstance.profile()).toEqual(PROFILE);
      expect(fixture.componentInstance.loading()).toBe(false);
    });
  });

  it('shows the avatar image when the avatar request succeeds', async () => {
    // #given/#when
    await setUp('success');

    // #then
    expect(fixture.componentInstance.avatarUrl()).not.toBeNull();
  });

  describe('onChangePassword', () => {
    it('should end the local session on success, since the backend revokes every session', async () => {
      // #given
      await setUp('success');
      profileService.changePassword.mockReturnValue(of(undefined));
      fixture.componentInstance.passwordForm.setValue({
        currentPassword: 'ancien-mot-de-passe',
        newPassword: 'nouveauMotDePasse123',
        confirmPassword: 'nouveauMotDePasse123',
      });

      // #when
      fixture.componentInstance.onChangePassword();

      // #then
      expect(authService.endLocalSession).toHaveBeenCalled();
    });

    it('should not end the local session when the backend rejects the change', async () => {
      // #given
      await setUp('success');
      profileService.changePassword.mockReturnValue(throwError(() => ({status: 400})));
      fixture.componentInstance.passwordForm.setValue({
        currentPassword: 'mauvais-mot-de-passe',
        newPassword: 'nouveauMotDePasse123',
        confirmPassword: 'nouveauMotDePasse123',
      });

      // #when
      fixture.componentInstance.onChangePassword();

      // #then
      expect(authService.endLocalSession).not.toHaveBeenCalled();
      expect(fixture.componentInstance.passwordError()).toBeTruthy();
    });
  });

  describe('onSaveInfo — email change (Partie 5)', () => {
    it('should require the current password when the email is changed', async () => {
      // #given
      await setUp('success');
      fixture.componentInstance.infoForm.patchValue({email: 'nouveau@assrone.ch', currentPassword: ''});

      // #when
      fixture.componentInstance.onSaveInfo();

      // #then
      expect(fixture.componentInstance.infoForm.errors?.['currentPasswordRequired']).toBeTruthy();
      expect(profileService.updateProfile).not.toHaveBeenCalled();
    });

    it('should not require a password when only the username/name change', async () => {
      // #given
      await setUp('success');
      fixture.componentInstance.infoForm.patchValue({username: 'nouveau-pseudo'});
      profileService.updateProfile.mockReturnValue(of(PROFILE));

      // #when
      fixture.componentInstance.onSaveInfo();

      // #then
      expect(profileService.updateProfile).toHaveBeenCalled();
      expect(authService.endLocalSession).not.toHaveBeenCalled();
    });

    it('should end the local session after a successful email change, since the backend revokes every session', async () => {
      // #given
      await setUp('success');
      profileService.updateProfile.mockReturnValue(of({...PROFILE, email: 'nouveau@assrone.ch'}));
      fixture.componentInstance.infoForm.patchValue({
        email: 'nouveau@assrone.ch',
        currentPassword: 'bon-mot-de-passe',
      });

      // #when
      fixture.componentInstance.onSaveInfo();

      // #then
      expect(profileService.updateProfile).toHaveBeenCalled();
      expect(authService.endLocalSession).toHaveBeenCalled();
    });
  });
});
