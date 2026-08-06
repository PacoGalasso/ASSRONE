import {Component, inject, OnInit, signal} from '@angular/core';
import {DatePipe} from '@angular/common';
import {SessionService} from '../../../core/auth/services/session-service';
import {AuthService} from '../../../core/auth/services/auth-service';
import {SessionInfo} from '../../../core/auth/models/session.model';

@Component({
  selector: 'app-profile-sessions',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './profile-sessions.html',
})
export class ProfileSessions implements OnInit {
  private sessionService = inject(SessionService);
  private authService = inject(AuthService);

  sessions = signal<SessionInfo[]>([]);
  loading = signal(true);
  loadError = signal<string | null>(null);
  actionError = signal<string | null>(null);
  actionMessage = signal<string | null>(null);
  revokingId = signal<string | null>(null);
  revokingOthers = signal(false);
  revokingAll = signal(false);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.sessionService.list().subscribe({
      next: (response) => {
        this.sessions.set(response.sessions);
        this.loading.set(false);
      },
      error: () => {
        this.loadError.set('Impossible de charger vos sessions actives.');
        this.loading.set(false);
      },
    });
  }

  onRevoke(session: SessionInfo): void {
    const question = session.current
      ? 'Déconnecter cette session mettra fin à votre session actuelle sur cet appareil. Continuer ?'
      : 'Déconnecter cette session ?';
    if (!confirm(question)) {
      return;
    }

    this.actionError.set(null);
    this.actionMessage.set(null);
    this.revokingId.set(session.id);

    this.sessionService.revoke(session.id).subscribe({
      next: () => {
        if (session.current) {
          // No refresh is ever attempted here: the refresh token backing
          // this tab was just revoked server-side, and retrying it would
          // only fail. Auth state is cleared immediately instead.
          this.authService.endLocalSession();
          return;
        }
        this.revokingId.set(null);
        this.sessions.update((list) => list.filter((s) => s.id !== session.id));
        this.actionMessage.set('Session déconnectée.');
      },
      error: () => {
        this.revokingId.set(null);
        this.actionError.set('Impossible de déconnecter cette session.');
      },
    });
  }

  onRevokeOthers(): void {
    if (!confirm('Déconnecter toutes les autres sessions actives ?')) {
      return;
    }

    this.actionError.set(null);
    this.actionMessage.set(null);
    this.revokingOthers.set(true);

    this.sessionService.revokeOthers().subscribe({
      next: (response) => {
        this.revokingOthers.set(false);
        this.sessions.update((list) => list.filter((s) => s.current));
        this.actionMessage.set(`${response.revokedCount} session(s) déconnectée(s).`);
      },
      error: () => {
        this.revokingOthers.set(false);
        this.actionError.set('Impossible de déconnecter les autres sessions.');
      },
    });
  }

  onRevokeAll(): void {
    if (!confirm('Déconnecter tous les appareils, y compris celui-ci ? Vous devrez vous reconnecter.')) {
      return;
    }

    this.actionError.set(null);
    this.actionMessage.set(null);
    this.revokingAll.set(true);

    this.sessionService.revokeAll().subscribe({
      next: () => this.authService.endLocalSession(),
      error: () => {
        this.revokingAll.set(false);
        this.actionError.set('Impossible de déconnecter tous les appareils.');
      },
    });
  }
}
