import {HttpClient} from '@angular/common/http';
import {Router} from '@angular/router';
import {catchError, finalize, Observable, of, tap, throwError} from 'rxjs';
import {AuthResponse, Credentials, RegisterRequest, RegisterResponse, User} from '../models/auth.models';
import {computed, Injectable, signal} from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly API_URL = '/auth';
  private readonly TOKEN_KEY = 'auth_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';
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
    return this.http.post<AuthResponse>(`${this.API_URL}/generateToken`, credentials).pipe(
      tap(response => this.setAuth(response))
    );
  }

  register(request: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.API_URL}/addNewUser`, request);
  }

  refreshToken(): Observable<AuthResponse> {
    const refreshToken = localStorage.getItem(this.REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token available'));
    }
    return this.http.post<AuthResponse>(`${this.API_URL}/refresh`, {refreshToken}).pipe(
      tap(response => this.setAuth(response))
    );
  }

  logout(): void {
    const refreshToken = localStorage.getItem(this.REFRESH_TOKEN_KEY);
    const request$ = refreshToken
      ? this.http.post<void>(`${this.API_URL}/logout`, {refreshToken})
      : of(undefined);

    request$.pipe(
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
    localStorage.setItem(this.REFRESH_TOKEN_KEY, response.refreshToken);
    localStorage.setItem(this.USER_KEY, JSON.stringify(user));
  }

  private clearAuth(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);

    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
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
