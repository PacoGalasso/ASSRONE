import {HttpClient} from '@angular/common/http';
import {Router} from '@angular/router';
import {catchError, map, Observable, of, tap} from 'rxjs';
import {
  AccountLifecycleMessageResponse,
  AuthResponse,
  Credentials,
  ForgotPasswordRequest,
  RegisterRequest,
  RegisterResponse,
  ResendVerificationRequest,
  ResetPasswordRequest,
  User,
  VerifyEmailRequest
} from '../models/auth.models';
import {computed, Injectable, OnDestroy, signal} from '@angular/core';

// Cross-tab logout sync only: the message is a fixed literal, never a token
// or any other credential — see AuthService#broadcastLogout.
const LOGOUT_BROADCAST_CHANNEL = 'assrone-auth';
const LOGOUT_MESSAGE = 'logout';

@Injectable({
  providedIn: 'root',
})
export class AuthService implements OnDestroy {
  private readonly API_URL = '/auth';

  // Access token lives only here, for the lifetime of this tab's JS realm:
  // never localStorage, never sessionStorage, never a JS-readable cookie.
  // Each browser tab therefore gets its own independent in-memory token, even
  // though the HttpOnly refresh cookie backing it is shared by the browser
  // across all tabs on this origin.
  private tokenSignal = signal<string | null>(null);
  isLoggedIn = computed(() => this.tokenSignal() !== null);
  private userSignal = signal<User | null>(null);
  user = computed(() => this.userSignal());

  private readonly logoutChannel: BroadcastChannel | null =
    typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel(LOGOUT_BROADCAST_CHANNEL) : null;

  constructor(
    private http: HttpClient,
    private router: Router
  ) {
    // Purely a same-origin, cross-tab UI sync: only ever clears this tab's own
    // in-memory state in reaction to another tab's logout. No token value is
    // ever part of the message, and no navigation is forced on this tab —
    // a user mid-task in another tab isn't redirected out from under them,
    // but any protected request they trigger next will correctly 401 (no
    // token attached) and follow the normal refresh-then-logout path, since
    // the refresh cookie was already revoked server-side by the tab that logged out.
    if (this.logoutChannel) {
      this.logoutChannel.onmessage = (event: MessageEvent) => {
        if (event.data === LOGOUT_MESSAGE) {
          this.clearAuth();
        }
      };
    }
  }

  ngOnDestroy(): void {
    this.logoutChannel?.close();
  }

  login(credentials: Credentials): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/generateToken`, credentials, {withCredentials: true}).pipe(
      tap(response => this.setAuth(response))
    );
  }

  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.API_URL}/addNewUser`, request);
  }

  // Deliberately never touches auth state: a successful reset must send the
  // user back to a fresh login, never log them in automatically (see
  // ResetPassword component).
  forgotPassword(request: ForgotPasswordRequest): Observable<AccountLifecycleMessageResponse> {
    return this.http.post<AccountLifecycleMessageResponse>(`${this.API_URL}/forgot-password`, request);
  }

  resetPassword(request: ResetPasswordRequest): Observable<AccountLifecycleMessageResponse> {
    return this.http.post<AccountLifecycleMessageResponse>(`${this.API_URL}/reset-password`, request);
  }

  verifyEmail(request: VerifyEmailRequest): Observable<AccountLifecycleMessageResponse> {
    return this.http.post<AccountLifecycleMessageResponse>(`${this.API_URL}/verify-email`, request);
  }

  resendVerification(request: ResendVerificationRequest): Observable<AccountLifecycleMessageResponse> {
    return this.http.post<AccountLifecycleMessageResponse>(`${this.API_URL}/resend-verification`, request);
  }

  // The refresh token itself is never available to this code: it lives in an
  // HttpOnly cookie the browser attaches automatically (withCredentials),
  // never in a body we could read or send.
  refreshToken(): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/refresh`, null, {withCredentials: true}).pipe(
      tap(response => this.setAuth(response))
    );
  }

  // Called exactly once, by the app initializer, before the router processes
  // the first navigation (see restore-session-initializer.ts). Deliberately
  // never errors: an absent/expired/invalid refresh cookie is a completely
  // normal outcome for a first-time or logged-out visitor, not a failure the
  // caller needs to react to — it just means "stay logged out", the same
  // state this service already starts in.
  restoreSession(): Observable<void> {
    return this.refreshToken().pipe(
      map(() => undefined),
      catchError(() => {
        this.clearAuth();
        return of(undefined);
      })
    );
  }

  // Always calls the backend, even with no visible client-side state to check:
  // whether a refresh cookie exists isn't something this code can ever know,
  // and logout must clear it server-side (and in the browser) regardless.
  logout(): void {
    this.http.post<void>(`${this.API_URL}/logout`, null, {withCredentials: true}).pipe(
      catchError(() => of(undefined))
    ).subscribe(() => {
      this.clearAuth();
      this.broadcastLogout();
      this.router.navigate(['/']);
    });
  }

  // Used when a session was already revoked server-side through a different
  // endpoint than /auth/logout — revoking the current session, or every
  // session, from "Sessions actives" (see SessionService). Clears local
  // state and syncs other tabs exactly like logout() does, but never calls
  // POST /auth/logout again (already redundant: the revocation already
  // happened) and never attempts a token refresh first, since the refresh
  // token backing this tab is already gone server-side.
  endLocalSession(): void {
    this.clearAuth();
    this.broadcastLogout();
    this.router.navigate(['/']);
  }

  isAdmin(): boolean {
    return this.userSignal()?.role === 'ROLE_ADMIN';
  }

  getToken(): string | null {
    return this.tokenSignal();
  }

  private setAuth(response: AuthResponse): void {
    const user: User = {
      username: response.username,
      role: response.role,
      email: response.username
    };

    this.tokenSignal.set(response.token);
    this.userSignal.set(user);
  }

  private clearAuth(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
  }

  private broadcastLogout(): void {
    this.logoutChannel?.postMessage(LOGOUT_MESSAGE);
  }
}
