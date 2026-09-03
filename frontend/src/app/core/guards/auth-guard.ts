import { inject } from '@angular/core';
import { AuthService } from '../services/auth';
import { CanActivateFn, Router } from '@angular/router';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  
  return auth.estaAutenticado() ? true : router.createUrlTree(['/login']);
};
