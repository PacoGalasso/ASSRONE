import {Routes} from '@angular/router';
import {authGuard} from './core/auth/guards/auth.guard';
import {adminGuard} from './core/auth/guards/admin.guard';

export const routes: Routes = [
  // ===== PUBLIC ROUTES (sans AppShell) =====
  // Délibérément PAS sous /auth/** : ce préfixe est réservé à l'API backend
  // (proxy.conf.json en dev, "location /auth/" côté nginx en production) et
  // route toute requête — y compris une navigation GET du navigateur — droit
  // vers Spring Boot, avant même que le routeur Angular ne s'exécute. Une
  // page SPA placée sous ce préfixe ne s'affiche donc jamais par navigation
  // directe ou F5 (voir HistoricalAccountsMigrationLoginTest et le rapport de
  // ce lot pour la démonstration du 401 qui en résultait sur /auth/reset-password
  // et /auth/verify-email).
  {
    path: 'login',
    loadComponent: () => import('./features/auth/pages/login/login').then(m => m.Login)
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/pages/forgot-password/forgot-password').then(m => m.ForgotPassword)
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/pages/reset-password/reset-password').then(m => m.ResetPassword)
  },
  {
    path: 'verify-email',
    loadComponent: () =>
      import('./features/auth/pages/verify-email/verify-email').then(m => m.VerifyEmail)
  },
  {
    path: 'resend-verification',
    loadComponent: () =>
      import('./features/auth/pages/resend-verification/resend-verification').then(m => m.ResendVerification)
  },

  // ===== PUBLIC PAGES (sans login requis) =====
  {
    path: 'membership/apply',
    loadComponent: () => import('./features/membership/apply/apply').then(m => m.Apply)
  }, {
    path: '',
    loadComponent: () => import('./shared/layouts/public-layout/public-layout').then(m => m.PublicLayout),
    children: [
      {
        path: '',
        loadComponent: () => import('./features/home/home').then(m => m.Home),
        pathMatch: 'full'
      },
      {
        path: 'about',
        loadComponent: () => import('./features/about/about').then(m => m.About)
      },
      {
        path: 'events',
        loadComponent: () => import('./features/events/events').then(m => m.default)
      },
      {
        path: 'membership',
        loadComponent: () => import('./features/membership/membership').then(m => m.Membership)
      },
      {
        path: 'contact',
        loadComponent: () => import('./features/contact/contact').then(m => m.Contact)
      },
      {
        path: 'statutes',
        loadComponent: () => import('./features/statutes/statutes').then(m => m.Statutes)
      },
      {
        path: 'charter',
        loadComponent: () => import('./features/charter/charter').then(m => m.Charter)
      }
    ]
  },

  // ===== PROTECTED ROUTES (AppShell + authGuard) =====
  {
    path: '',
    loadComponent: () => import('./shared/layouts/app-shell/app-shell').then(m => m.AppShell),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        redirectTo: 'members-portal',
        pathMatch: 'full',
      },
      {
        path: 'members-portal',
        loadComponent: () => import('./features/members-portal/members-portal').then(m => m.MembersPortal)
      },
      {
        path: 'profile',
        loadComponent: () => import('./features/profile/profile').then(m => m.Profile)
      },
      {
        path: 'formations',
        loadComponent: () => import('./features/formations/formations').then(m => m.Formations)
      },
      {
        path: 'events/create',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/create-event/create-event').then(m => m.CreateEvent)
      },
      {
        path: 'events/:id/edit',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/edit-event/edit-event').then(m => m.EditEvent)
      },
      {
        path: 'admin/membership',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/admin-membership/admin-membership').then(m => m.AdminMembership)
      },
      {
        path: 'admin/committee',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/admin-committee/admin-committee').then(m => m.AdminCommittee)
      },
    ]
  },

  // ===== CATCH ALL =====
  {
    path: '**',
    redirectTo: ''
  }
];
