import {HttpClient} from '@angular/common/http';
import {Router} from '@angular/router';
import {Observable, tap} from 'rxjs';
import {AuthResponse, Credentials, User} from '../models/auth.models';
import {computed, Injectable, signal} from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly API_URL = 'http://localhost:8081/auth';
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

  register(userData: any): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_URL}/addNewUser`, userData).pipe(
      tap(response => this.setAuth(response))
    );
  }

  refreshToken(): Observable<AuthResponse> {
    const refreshToken = localStorage.getItem(this.REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      throw new Error('No refresh token available');
    }
    return this.http.post<AuthResponse>(`${this.API_URL}/refresh`, {refreshToken}).pipe(
      tap(response => this.setAuth(response))
    );
  }

  logout(): void {
    this.clearAuth();
    this.router.navigate(['/auth/login']);
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
