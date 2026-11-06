import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';
import { clienteGuard } from './core/guards/cliente-guard';
import { gerenteGuard } from './core/guards/gerente-guard';
import { visitanteGuard } from './core/guards/visitante-guard';

export const routes: Routes = [
   {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },
  {
    path: 'login',
    canActivate: [visitanteGuard],
    loadComponent: () =>
      import('./features/auth/pages/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    canActivate: [visitanteGuard],
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
  canActivate: [clienteGuard],
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
    path: 'deposito',
    canActivate: [clienteGuard],
    loadComponent: () =>
      import('./features/cliente/pages/deposito/deposito').then(
        (m) => m.Deposito,
      ),
  },
  {
    path: 'saque',
    canActivate: [clienteGuard],
    loadComponent: () =>
      import('./features/cliente/pages/saque/saque').then(
        (m) => m.Saque,
      ),
  },
  {
    path: 'cliente',
    canActivate: [clienteGuard],
    loadComponent: () =>
      import('./features/cliente/pages/home/home').then(
        (m) => m.Home,
      ),
  },
  {
    path: 'gerente',
    canActivate: [gerenteGuard],
    loadComponent: () =>
      import('./features/gerente/pages/home/home').then(
        (m) => m.Home,
      ),
  },
  {
    path: 'gerente/clientes',
    canActivate: [gerenteGuard],
    loadComponent: () =>
      import('./features/gerente/pages/clientes/clientes').then(
        (m) => m.Clientes,
      ),
  },
  {
    path: 'gerente/relatorio',
    canActivate: [gerenteGuard],
    loadComponent: () =>
      import('./features/gerente/pages/relatorio/relatorio').then(
        (m) => m.Relatorio,
      ),
  },
  {
    path: 'gerente/gerentes',
    canActivate: [gerenteGuard],
    loadComponent: () =>
      import('./features/gerente/pages/gerentes/gerentes').then(
        (m) => m.Gerentes,
      ),
  },
  {
    path: 'gerente/gerentes/novo',
    canActivate: [gerenteGuard],
    loadComponent: () =>
      import('./features/gerente/pages/novo-gerente/novo-gerente').then(
        (m) => m.NovoGerente,
      ),
  },
  {
    path: 'gerente/gerentes/:cpf/editar',
    canActivate: [gerenteGuard],
    loadComponent: () =>
      import('./features/gerente/pages/editar-gerente/editar-gerente').then(
        (m) => m.EditarGerente,
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
