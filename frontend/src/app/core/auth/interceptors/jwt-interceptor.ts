import {inject, Injectable} from '@angular/core';
import {HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest} from '@angular/common/http';
import {BehaviorSubject, Observable, throwError} from 'rxjs';
import {catchError, filter, switchMap, take} from 'rxjs/operators';
import {AuthService} from '../services/auth-service';

interface RefreshState {
  token: string | null;
  error: unknown | null;
}

const PUBLIC_AUTH_ENDPOINTS = [
  '/auth/generateToken',
  '/auth/addNewUser',
  '/auth/welcome',
  '/auth/refresh',
  '/auth/logout',
  // Token-based, unauthenticated account-lifecycle endpoints: a 400/404/410 on
  // an invalid or expired token here is never a signal that the current
  // session's own access token is stale, so it must never enter the
  // refresh-then-logout path below (see Partie 12 of the account-lifecycle lot).
  '/auth/forgot-password',
  '/auth/reset-password',
  '/auth/verify-email',
  '/auth/resend-verification'
];

@Injectable()
export class JwtInterceptor implements HttpInterceptor {
  private authService = inject(AuthService);
  private isRefreshing = false;
  private refreshState$ = new BehaviorSubject<RefreshState>({token: null, error: null});

  intercept(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    if (PUBLIC_AUTH_ENDPOINTS.some(endpoint => request.url.includes(endpoint))) {
      return next.handle(request);
    }

    const token = this.authService.getToken();

    if (token) {
      request = request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      });
    }

    return next.handle(request).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401) {
          return this.handle401Error(request, next);
        }
        return throwError(() => error);
      })
    );
  }

  private handle401Error(request: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    if (!this.isRefreshing) {
      this.isRefreshing = true;
      this.refreshState$.next({token: null, error: null});

      return this.authService.refreshToken().pipe(
        switchMap(response => {
          this.isRefreshing = false;
          this.refreshState$.next({token: response.token, error: null});
          return next.handle(this.addToken(request, response.token));
        }),
        catchError(err => {
          this.isRefreshing = false;
          this.refreshState$.next({token: null, error: err});
          this.authService.logout();
          return throwError(() => err);
        })
      );
    }

    return this.refreshState$.pipe(
      filter(state => state.token !== null || state.error !== null),
      take(1),
      switchMap(state => {
        if (state.error !== null) {
          return throwError(() => state.error);
        }
        return next.handle(this.addToken(request, state.token as string));
      })
    );
  }

  private addToken(request: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
    return request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }
}
