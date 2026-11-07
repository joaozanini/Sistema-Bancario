import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth';

// Telas do perfil GERENTE (R8 a R16): cliente logado volta para a própria tela inicial
export const gerenteGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const tipo = auth.obterTipo();

  if (tipo === 'GERENTE') {
    return true;
  }

  const destino = tipo === 'CLIENTE' ? '/cliente' : '/login';
  return router.createUrlTree([destino]);
};
