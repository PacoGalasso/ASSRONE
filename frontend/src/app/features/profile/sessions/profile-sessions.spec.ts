import {ComponentFixture, TestBed} from '@angular/core/testing';
import {of, Subject, throwError} from 'rxjs';
import {ProfileSessions} from './profile-sessions';
import {SessionService} from '../../../core/auth/services/session-service';
import {AuthService} from '../../../core/auth/services/auth-service';
import {SessionInfo} from '../../../core/auth/models/session.model';

const CURRENT: SessionInfo = {
  id: 'current-id',
  current: true,
  createdAt: '2026-08-01T10:00:00',
  lastUsedAt: '2026-08-05T10:00:00',
  expiresAt: '2026-08-12T10:00:00',
  ipAddress: '203.0.113.10',
  device: 'Chrome sur Windows',
};
const OTHER: SessionInfo = {
  id: 'other-id',
  current: false,
  createdAt: '2026-07-01T10:00:00',
  lastUsedAt: '2026-07-20T10:00:00',
  expiresAt: '2026-08-01T10:00:00',
  ipAddress: '198.51.100.5',
  device: 'Safari sur iPhone',
};

describe('ProfileSessions', () => {
  let component: ProfileSessions;
  let fixture: ComponentFixture<ProfileSessions>;
  let sessionService: {
    list: ReturnType<typeof vi.fn>;
    revoke: ReturnType<typeof vi.fn>;
    revokeOthers: ReturnType<typeof vi.fn>;
    revokeAll: ReturnType<typeof vi.fn>;
  };
  let authService: { endLocalSession: ReturnType<typeof vi.fn> };

  async function setUp(sessions: SessionInfo[]): Promise<void> {
    sessionService = {
      list: vi.fn().mockReturnValue(of({sessions})),
      revoke: vi.fn(),
      revokeOthers: vi.fn(),
      revokeAll: vi.fn(),
    };
    authService = {endLocalSession: vi.fn()};

    await TestBed.configureTestingModule({
      imports: [ProfileSessions],
      providers: [
        {provide: SessionService, useValue: sessionService},
        {provide: AuthService, useValue: authService},
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileSessions);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('should create', async () => {
    // #given / #when
    await setUp([CURRENT]);

    // #then
    expect(component).toBeTruthy();
  });

  describe('loading', () => {
    it('shows a loading state before the sessions arrive', async () => {
      // #given
      sessionService = {
        list: vi.fn().mockReturnValue(of({sessions: []})),
        revoke: vi.fn(),
        revokeOthers: vi.fn(),
        revokeAll: vi.fn(),
      };
      authService = {endLocalSession: vi.fn()};
      await TestBed.configureTestingModule({
        imports: [ProfileSessions],
        providers: [
          {provide: SessionService, useValue: sessionService},
          {provide: AuthService, useValue: authService},
        ],
      }).compileComponents();
      fixture = TestBed.createComponent(ProfileSessions);
      component = fixture.componentInstance;

      // #when / #then: signal starts true before ngOnInit resolves anything
      expect(component.loading()).toBe(true);
    });
  });

  describe('list', () => {
    it('exposes the sessions returned by the service', async () => {
      // #given / #when
      await setUp([CURRENT, OTHER]);

      // #then
      expect(component.sessions()).toEqual([CURRENT, OTHER]);
      expect(component.loading()).toBe(false);
    });

    it('shows an empty state when there are no active sessions', async () => {
      // #given / #when
      await setUp([]);

      // #then
      expect(component.sessions()).toEqual([]);
      expect(fixture.nativeElement.textContent).toContain('Aucune session active');
    });

    it('shows an error state when loading fails', async () => {
      // #given
      sessionService = {
        list: vi.fn().mockReturnValue(throwError(() => new Error('boom'))),
        revoke: vi.fn(),
        revokeOthers: vi.fn(),
        revokeAll: vi.fn(),
      };
      authService = {endLocalSession: vi.fn()};
      await TestBed.configureTestingModule({
        imports: [ProfileSessions],
        providers: [
          {provide: SessionService, useValue: sessionService},
          {provide: AuthService, useValue: authService},
        ],
      }).compileComponents();
      fixture = TestBed.createComponent(ProfileSessions);
      component = fixture.componentInstance;

      // #when
      fixture.detectChanges();
      await fixture.whenStable();

      // #then
      expect(component.loadError()).toBeTruthy();
      expect(component.loading()).toBe(false);
    });
  });

  describe('onRevoke', () => {
    it('asks for confirmation before revoking a session', async () => {
      // #given
      await setUp([CURRENT, OTHER]);
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

      // #when
      component.onRevoke(OTHER);

      // #then
      expect(confirmSpy).toHaveBeenCalled();
      expect(sessionService.revoke).not.toHaveBeenCalled();
    });

    it('revokes a non-current session and removes it from the list without touching auth state', async () => {
      // #given
      await setUp([CURRENT, OTHER]);
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      sessionService.revoke.mockReturnValue(of(undefined));

      // #when
      component.onRevoke(OTHER);

      // #then
      expect(sessionService.revoke).toHaveBeenCalledWith('other-id');
      expect(component.sessions()).toEqual([CURRENT]);
      expect(authService.endLocalSession).not.toHaveBeenCalled();
    });

    it('disables the button for the session currently being revoked', async () => {
      // #given
      await setUp([CURRENT, OTHER]);
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const pending = new Subject<void>();
      sessionService.revoke.mockReturnValue(pending.asObservable());

      // #when
      component.onRevoke(OTHER);

      // #then
      expect(component.revokingId()).toBe('other-id');
    });

    it('clears local auth state and never re-fetches sessions when revoking the current session', async () => {
      // #given
      await setUp([CURRENT, OTHER]);
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      sessionService.revoke.mockReturnValue(of(undefined));
      sessionService.list.mockClear();

      // #when
      component.onRevoke(CURRENT);

      // #then
      expect(authService.endLocalSession).toHaveBeenCalled();
      expect(sessionService.list).not.toHaveBeenCalled();
    });

    it('shows an error and re-enables the button when revocation fails', async () => {
      // #given
      await setUp([CURRENT, OTHER]);
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      sessionService.revoke.mockReturnValue(throwError(() => new Error('boom')));

      // #when
      component.onRevoke(OTHER);

      // #then
      expect(component.actionError()).toBeTruthy();
      expect(component.revokingId()).toBeNull();
    });
  });

  describe('onRevokeOthers', () => {
    it('asks for confirmation first', async () => {
      // #given
      await setUp([CURRENT, OTHER]);
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

      // #when
      component.onRevokeOthers();

      // #then
      expect(confirmSpy).toHaveBeenCalled();
      expect(sessionService.revokeOthers).not.toHaveBeenCalled();
    });

    it('keeps only the current session in the list on success', async () => {
      // #given
      await setUp([CURRENT, OTHER]);
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      sessionService.revokeOthers.mockReturnValue(of({revokedCount: 1}));

      // #when
      component.onRevokeOthers();

      // #then
      expect(component.sessions()).toEqual([CURRENT]);
      expect(authService.endLocalSession).not.toHaveBeenCalled();
    });
  });

  describe('onRevokeAll', () => {
    it('asks for confirmation first', async () => {
      // #given
      await setUp([CURRENT, OTHER]);
      const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);

      // #when
      component.onRevokeAll();

      // #then
      expect(confirmSpy).toHaveBeenCalled();
      expect(sessionService.revokeAll).not.toHaveBeenCalled();
    });

    it('clears local auth state on success, without attempting a refresh', async () => {
      // #given
      await setUp([CURRENT, OTHER]);
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      sessionService.revokeAll.mockReturnValue(of(undefined));

      // #when
      component.onRevokeAll();

      // #then
      expect(authService.endLocalSession).toHaveBeenCalled();
    });
  });

  describe('accessibility', () => {
    it('renders an aria-label on each revoke button identifying the device', async () => {
      // #given / #when
      await setUp([CURRENT, OTHER]);

      // #then
      const buttons: HTMLButtonElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('button[aria-label^="Déconnecter la session"]')
      );
      expect(buttons.length).toBe(2);
    });
  });
});
