import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth';

export const clienteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const tipo = auth.obterTipo();

  if (tipo === 'CLIENTE') {
    return true;
  }

  const destino = tipo === 'GERENTE' ? '/gerente' : tipo === 'ADMIN' ? '/admin' : '/login';
  return router.createUrlTree([destino]);
};
