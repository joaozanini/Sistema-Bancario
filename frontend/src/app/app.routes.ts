import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';
import { clienteGuard } from './core/guards/cliente-guard';

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
    path: 'register',
    loadComponent: () =>
      import('./features/auth/pages/register/register').then((m) => m.Register),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./layout/main-layout/main-layout')
        .then((m) => m.MainLayout),

        children: [
          {
  path: 'extrato',
  loadComponent: () =>
    import('./features/cliente/pages/extrato/extrato').then((m) => m.Extrato),
},
  {
    path: 'transferencia',
    canActivate: [clienteGuard],
    loadComponent: () =>
      import('./features/cliente/pages/transferencia/transferencia').then(
        (m) => m.Transferencia,
      ),
  },
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
    path: 'gerente/gerentes',
    loadComponent: () =>
      import('./features/gerente/pages/gerentes/gerentes').then(
        (m) => m.Gerentes,
      ),
  },
  {
    path: 'gerente/gerentes/:cpf/editar',
    loadComponent: () =>
      import('./features/gerente/pages/editar-gerente/editar-gerente').then(
        (m) => m.EditarGerente,
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
