import {HttpClient} from '@angular/common/http';
import {Router} from '@angular/router';
import {catchError, finalize, Observable, of, tap} from 'rxjs';
import {AuthResponse, Credentials, RegisterRequest, RegisterResponse, User} from '../models/auth.models';
import {computed, Injectable, signal} from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly API_URL = '/auth';
  private readonly TOKEN_KEY = 'auth_token';
  private readonly USER_KEY = 'auth_user';

  private tokenSignal = signal<string | null>(this.loadToken());
  isLoggedIn = computed(() => this.tokenSignal() !== null);
  private userSignal = signal<User | null>(this.loadUser());
  user = computed(() => this.userSignal());

  constructor(
    private http: HttpClient,
    private router: Router
  ) {
  }

  login(credentials: Credentials): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/generateToken`, credentials, {withCredentials: true}).pipe(
      tap(response => this.setAuth(response))
    );
  }

  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.API_URL}/addNewUser`, request);
  }

  // The refresh token itself is never available to this code: it lives in an
  // HttpOnly cookie the browser attaches automatically (withCredentials),
  // never in a body we could read or send.
  refreshToken(): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/refresh`, null, {withCredentials: true}).pipe(
      tap(response => this.setAuth(response))
    );
  }

  // Always calls the backend, even with no visible client-side state to check:
  // whether a refresh cookie exists isn't something this code can ever know,
  // and logout must clear it server-side (and in the browser) regardless.
  logout(): void {
    this.http.post<void>(`${this.API_URL}/logout`, null, {withCredentials: true}).pipe(
      catchError(() => of(undefined)),
      finalize(() => {
        this.clearAuth();
        this.router.navigate(['/']);
      })
    ).subscribe();
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

    localStorage.setItem(this.TOKEN_KEY, response.token);
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  private clearAuth(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);

    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }

  private loadToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private loadUser(): User | null {
    const user = localStorage.getItem(this.USER_KEY);
    return user ? JSON.parse(user) : null;
  }
}
