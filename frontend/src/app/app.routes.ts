import {Routes} from '@angular/router';
import {authGuard} from './core/auth/guards/auth.guard';
import {adminGuard} from './core/auth/guards/admin.guard';

export const routes: Routes = [
  // ===== PUBLIC ROUTES (sans AppShell) =====
  {
    path: 'auth',
    children: [
      {
        path: 'login',
        loadComponent: () => import('./features/auth/pages/login/login').then(m => m.Login)
      }
    ]
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

      // ===== ADMIN ROUTES =====
      {
        path: 'admin',
        canActivate: [adminGuard],
        children: [
          {
            path: 'documents',
            loadComponent: () => import('./features/admin-documents/admin-documents').then(m => m.AdminDocuments)
          },
          {
            path: 'users',
            loadComponent: () => import('./features/admin-users/admin-users').then(m => m.AdminUsers)
          }
        ]
      }
    ]
  },

  // ===== CATCH ALL =====
  {
    path: '**',
    redirectTo: ''
  }
];
