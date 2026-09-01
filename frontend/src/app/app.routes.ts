import { Routes } from '@angular/router';

export const routes: Routes = [
   {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/pages/login/login').then((m) => m.Login),
  },
  {
    path: '',
    loadComponent: () =>
      import('./layout/main-layout/main-layout')
        .then((m) => m.MainLayout),

        children: [
  {
    path: 'cliente',
    loadComponent: () =>
      import('./features/cliente/pages/home/home').then(
        (m) => m.Home,
      ),
  },
  {
    path: 'gerente',
    loadComponent: () =>
      import('./features/gerente/pages/home/home').then(
        (m) => m.Home,
      ),
  },
  {
    path: 'admin',
    loadComponent: () =>
      import('./features/admin/pages/home/home').then(
        (m) => m.Home,
      ),
      },
    ],
  },
  {
    path: '**',
    loadComponent: () =>
      import('./shared/pages/not-found/not-found').then(
        (m) => m.NotFound,
      ),
  },
];