// main.ts
import {bootstrapApplication} from '@angular/platform-browser';
import {App} from './app/app';
import {routes} from './app/app.routes';
import {provideRouter, withInMemoryScrolling} from '@angular/router';
import {provideAppInitializer, provideBrowserGlobalErrorListeners} from '@angular/core';
import {HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi} from '@angular/common/http';
import {JwtInterceptor} from './app/core/auth/interceptors/jwt-interceptor';
import {restoreSessionInitializer} from './app/core/auth/init/restore-session-initializer';

bootstrapApplication(App, {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withInMemoryScrolling({
        scrollPositionRestoration: 'top',
        anchorScrolling: 'enabled',
      })
    ),
    provideHttpClient(withInterceptorsFromDi()),
    {provide: HTTP_INTERCEPTORS, useClass: JwtInterceptor, multi: true},
    provideAppInitializer(restoreSessionInitializer),
  ],
});
