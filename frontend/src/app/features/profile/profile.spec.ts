import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { Profile } from './profile';
import { ProfileService } from '../../core/auth/services/profile-service';
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
  };

  async function setUp(avatarResult: 'success' | HttpErrorResponse): Promise<void> {
    profileService = {
      getProfile: vi.fn().mockReturnValue(of(PROFILE)),
      getAvatar: vi.fn().mockReturnValue(
        avatarResult === 'success' ? of(new Blob(['fake-image'])) : throwError(() => avatarResult)
      ),
    };

    await TestBed.configureTestingModule({
      imports: [Profile],
      providers: [{provide: ProfileService, useValue: profileService}],
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
});
