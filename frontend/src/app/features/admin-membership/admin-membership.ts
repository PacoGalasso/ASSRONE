// features/admin-membership/admin-membership.ts
import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {DatePipe} from '@angular/common';
import {MembershipApplicationService} from '../../core/auth/services/membership-application-service';
import {AdminUserService} from '../../core/auth/services/admin-user-service';
import {MembershipApplicationDto} from '../../core/auth/models/membership-application.model';
import {UserProfile} from '../../core/auth/models/profile.model';

@Component({
  selector: 'app-admin-membership',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './admin-membership.html',
})
export class AdminMembership implements OnInit {
  private applicationService = inject(MembershipApplicationService);
  private userService = inject(AdminUserService);

  applications = signal<MembershipApplicationDto[]>([]);
  users = signal<UserProfile[]>([]);
  loadingApplications = signal(true);
  loadingUsers = signal(true);
  processingId = signal<number | null>(null);
  errorMessage = signal<string | null>(null);

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
}
