import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth';

export const visitanteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.estaAutenticado()) {
    return true;
  }

  const tipo = auth.obterTipo();
  if (tipo === 'GERENTE') {
    return router.createUrlTree(['/gerente']);
  }
  if (tipo === 'CLIENTE') {
    return router.createUrlTree(['/cliente']);
  }

  auth.logout();
  return true;
};
