import {ComponentFixture, TestBed} from '@angular/core/testing';
import {of, Subject, throwError} from 'rxjs';
import {AdminMembership} from './admin-membership';
import {AdminUserService} from '../../core/auth/services/admin-user-service';
import {MembershipApplicationService} from '../../core/auth/services/membership-application-service';
import {AuthService} from '../../core/auth/services/auth-service';
import {UserProfile} from '../../core/auth/models/profile.model';

const ADMIN_USER: UserProfile = {
  id: 1, email: 'admin@assrone.ch', username: 'admin', firstName: 'Admin', lastName: 'Un',
  role: 'ADMIN', createdAt: '2026-01-01T00:00:00',
};
const OTHER_USER: UserProfile = {
  id: 2, email: 'membre@assrone.ch', username: 'membre', firstName: 'Membre', lastName: 'Deux',
  role: 'USER', createdAt: '2026-01-02T00:00:00',
};

describe('AdminMembership', () => {
  let component: AdminMembership;
  let fixture: ComponentFixture<AdminMembership>;
  let adminUserService: {
    getAll: ReturnType<typeof vi.fn>;
    changeRole: ReturnType<typeof vi.fn>;
    deleteUser: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    adminUserService = {
      getAll: vi.fn().mockReturnValue(of([ADMIN_USER, OTHER_USER])),
      changeRole: vi.fn(),
      deleteUser: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AdminMembership],
      providers: [
        {provide: AdminUserService, useValue: adminUserService},
        {
          provide: MembershipApplicationService,
          useValue: {getAll: vi.fn().mockReturnValue(of([])), accept: vi.fn(), reject: vi.fn()},
        },
        {provide: AuthService, useValue: {user: () => ADMIN_USER}},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminMembership);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('loads users and exposes them for display', () => {
    // #then
    expect(component.users()).toEqual([ADMIN_USER, OTHER_USER]);
  });

  it('isSelf identifies the currently logged-in admin by email', () => {
    // #then
    expect(component.isSelf(ADMIN_USER)).toBe(true);
    expect(component.isSelf(OTHER_USER)).toBe(false);
  });

  describe('role change', () => {
    it('asks for confirmation before saving a role change', () => {
      // #given
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
      adminUserService.changeRole.mockReturnValue(of(undefined));

      // #when
      component.confirmRoleChange(OTHER_USER, 'ADMIN');

      // #then
      expect(confirmSpy).toHaveBeenCalled();
      expect(adminUserService.changeRole).not.toHaveBeenCalled();
    });

    it('calls the service with the target id and selected role when confirmed', () => {
      // #given
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      adminUserService.changeRole.mockReturnValue(of(undefined));

      // #when
      component.confirmRoleChange(OTHER_USER, 'ADMIN');

      // #then
      expect(adminUserService.changeRole).toHaveBeenCalledWith(OTHER_USER.id, 'ADMIN');
    });

    it('does not update the local list before the backend confirms success', () => {
      // #given
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const pending = new Subject<void>();
      adminUserService.changeRole.mockReturnValue(pending.asObservable());

      // #when
      component.confirmRoleChange(OTHER_USER, 'ADMIN');

      // #then
      expect(component.users().find(u => u.id === OTHER_USER.id)?.role).toBe('USER');

      // #when
      pending.next();
      pending.complete();

      // #then
      expect(component.users().find(u => u.id === OTHER_USER.id)?.role).toBe('ADMIN');
    });

    it('surfaces a 409 conflict as a last-administrator error message', () => {
      // #given
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      adminUserService.changeRole.mockReturnValue(throwError(() => ({status: 409})));

      // #when
      component.confirmRoleChange(OTHER_USER, 'ADMIN');

      // #then
      expect(component.userErrorMessage()).toContain('dernier administrateur');
      expect(component.users().find(u => u.id === OTHER_USER.id)?.role).toBe('USER');
    });

    it('surfaces a 403 as a forbidden-action error message', () => {
      // #given
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      adminUserService.changeRole.mockReturnValue(throwError(() => ({status: 403})));

      // #when
      component.confirmRoleChange(OTHER_USER, 'ADMIN');

      // #then
      expect(component.userErrorMessage()).toContain('interdite');
    });

    it('surfaces a 404 as a not-found error message', () => {
      // #given
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      adminUserService.changeRole.mockReturnValue(throwError(() => ({status: 404})));

      // #when
      component.confirmRoleChange(OTHER_USER, 'ADMIN');

      // #then
      expect(component.userErrorMessage()).toContain("n'existe plus");
    });
  });

  describe('user deletion', () => {
    it('asks for confirmation before deleting', () => {
      // #given
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

      // #when
      component.onDeleteUser(OTHER_USER);

      // #then
      expect(confirmSpy).toHaveBeenCalled();
      expect(adminUserService.deleteUser).not.toHaveBeenCalled();
    });

    it('removes the user from the local list only after backend success', () => {
      // #given
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const pending = new Subject<void>();
      adminUserService.deleteUser.mockReturnValue(pending.asObservable());

      // #when
      component.onDeleteUser(OTHER_USER);

      // #then
      expect(component.users().some(u => u.id === OTHER_USER.id)).toBe(true);

      // #when
      pending.next();
      pending.complete();

      // #then
      expect(component.users().some(u => u.id === OTHER_USER.id)).toBe(false);
    });

    it('keeps the user in the list and surfaces an error on 409', () => {
      // #given
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      adminUserService.deleteUser.mockReturnValue(throwError(() => ({status: 409})));

      // #when
      component.onDeleteUser(OTHER_USER);

      // #then
      expect(component.users().some(u => u.id === OTHER_USER.id)).toBe(true);
      expect(component.userErrorMessage()).toContain('dernier administrateur');
    });

    it('surfaces a 403 as a forbidden-action error message', () => {
      // #given
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      adminUserService.deleteUser.mockReturnValue(throwError(() => ({status: 403})));

      // #when
      component.onDeleteUser(OTHER_USER);

      // #then
      expect(component.userErrorMessage()).toContain('interdite');
    });
  });

  it('the delete button is disabled for the currently logged-in admin', () => {
    // #then
    const deleteButtons = fixture.nativeElement.querySelectorAll('td:last-child button');
    const adminRowIndex = component.users().findIndex(u => u.id === ADMIN_USER.id);
    expect(deleteButtons[adminRowIndex].disabled).toBe(true);
  });

  it('the modify-role button is disabled for the currently logged-in admin', () => {
    // #then
    const modifyButtons = fixture.nativeElement.querySelectorAll('button[title="Modifier le rôle"]');
    const adminRowIndex = component.users().findIndex(u => u.id === ADMIN_USER.id);
    expect(modifyButtons[adminRowIndex].disabled).toBe(true);
  });
});
