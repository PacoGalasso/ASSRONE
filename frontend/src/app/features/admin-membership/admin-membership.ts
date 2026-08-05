// features/admin-membership/admin-membership.ts
import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {DatePipe} from '@angular/common';
import {MembershipApplicationService} from '../../core/auth/services/membership-application-service';
import {AdminUserService} from '../../core/auth/services/admin-user-service';
import {AuthService} from '../../core/auth/services/auth-service';
import {MembershipApplicationDto} from '../../core/auth/models/membership-application.model';
import {AdminUserDto, Role} from '../../core/auth/models/profile.model';

@Component({
  selector: 'app-admin-membership',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './admin-membership.html',
})
export class AdminMembership implements OnInit {
  private applicationService = inject(MembershipApplicationService);
  private userService = inject(AdminUserService);
  private authService = inject(AuthService);

  readonly roles: Role[] = ['USER', 'ADMIN'];

  applications = signal<MembershipApplicationDto[]>([]);
  users = signal<AdminUserDto[]>([]);
  loadingApplications = signal(true);
  loadingUsers = signal(true);
  processingId = signal<number | null>(null);
  errorMessage = signal<string | null>(null);

  editingRoleId = signal<number | null>(null);
  updatingRoleId = signal<number | null>(null);
  deletingUserId = signal<number | null>(null);
  userErrorMessage = signal<string | null>(null);

  pendingApplications = computed(() => this.applications().filter(a => a.status === 'PENDING'));

  ngOnInit(): void {
    this.loadApplications();
    this.loadUsers();
  }

  onAccept(application: MembershipApplicationDto): void {
    this.processingId.set(application.id);
    this.errorMessage.set(null);
    this.applicationService.accept(application.id).subscribe({
      next: (updated) => {
        this.applications.update(list => list.map(a => a.id === updated.id ? updated : a));
        this.processingId.set(null);
        this.loadUsers();
      },
      error: (err) => {
        this.errorMessage.set(
          err.status === 409 ? 'Un compte existe déjà avec cet email.' : "Une erreur est survenue lors de l'acceptation."
        );
        this.processingId.set(null);
      },
    });
  }

  onReject(application: MembershipApplicationDto): void {
    this.processingId.set(application.id);
    this.errorMessage.set(null);
    this.applicationService.reject(application.id).subscribe({
      next: (updated) => {
        this.applications.update(list => list.map(a => a.id === updated.id ? updated : a));
        this.processingId.set(null);
      },
      error: () => {
        this.errorMessage.set('Une erreur est survenue lors du refus.');
        this.processingId.set(null);
      },
    });
  }

  private loadApplications(): void {
    this.loadingApplications.set(true);
    this.applicationService.getAll().subscribe({
      next: (data) => {
        this.applications.set(data);
        this.loadingApplications.set(false);
      },
      error: () => this.loadingApplications.set(false),
    });
  }

  private loadUsers(): void {
    this.loadingUsers.set(true);
    this.userService.getAll().subscribe({
      next: (data) => {
        this.users.set(data);
        this.loadingUsers.set(false);
      },
      error: () => this.loadingUsers.set(false),
    });
  }

  isSelf(user: AdminUserDto): boolean {
    const email = this.authService.user()?.email;
    return !!email && email.toLowerCase() === user.email.toLowerCase();
  }

  startRoleEdit(user: AdminUserDto): void {
    this.userErrorMessage.set(null);
    this.editingRoleId.set(user.id);
  }

  cancelRoleEdit(): void {
    this.editingRoleId.set(null);
  }

  confirmRoleChange(user: AdminUserDto, selectedRole: string): void {
    const newRole = selectedRole as Role;
    if (newRole === user.role) {
      this.editingRoleId.set(null);
      return;
    }
    if (!confirm(`Changer le rôle de ${user.firstName} ${user.lastName} (${user.email}) : ${user.role} → ${newRole} ?`)) {
      return;
    }

    this.updatingRoleId.set(user.id);
    this.userErrorMessage.set(null);
    this.userService.changeRole(user.id, newRole).subscribe({
      next: () => {
        this.users.update(list => list.map(u => u.id === user.id ? {...u, role: newRole} : u));
        this.updatingRoleId.set(null);
        this.editingRoleId.set(null);
      },
      error: (err) => {
        this.userErrorMessage.set(this.roleChangeErrorMessage(err.status));
        this.updatingRoleId.set(null);
      },
    });
  }

  onDeleteUser(user: AdminUserDto): void {
    if (!confirm(`Supprimer définitivement le membre "${user.firstName} ${user.lastName}" (${user.email}) ? Cette action est irréversible.`)) {
      return;
    }

    this.deletingUserId.set(user.id);
    this.userErrorMessage.set(null);
    this.userService.deleteUser(user.id).subscribe({
      next: () => {
        this.users.update(list => list.filter(u => u.id !== user.id));
        this.deletingUserId.set(null);
      },
      error: (err) => {
        this.userErrorMessage.set(this.deleteUserErrorMessage(err.status));
        this.deletingUserId.set(null);
      },
    });
  }

  private roleChangeErrorMessage(status: number): string {
    if (status === 403) {
      return 'Action interdite : vous ne pouvez pas modifier ce rôle.';
    }
    if (status === 404) {
      return 'Ce membre n\'existe plus.';
    }
    if (status === 409) {
      return 'Impossible de rétrograder le dernier administrateur.';
    }
    return 'Une erreur est survenue lors du changement de rôle.';
  }

  private deleteUserErrorMessage(status: number): string {
    if (status === 403) {
      return 'Action interdite : vous ne pouvez pas supprimer ce compte.';
    }
    if (status === 404) {
      return 'Ce membre n\'existe plus.';
    }
    if (status === 409) {
      return 'Impossible de supprimer le dernier administrateur.';
    }
    return 'Une erreur est survenue lors de la suppression.';
  }
}
