import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { AuthService } from '../services/auth';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.obterToken();

  const request = token ? 
  req.clone({
    setHeaders: {
      'x-acess-token': token,
    },
  })
  : req;

  return next(request).pipe(
    catchError((erro: HttpErrorResponse) => {
    if(erro.status === 401) {
      auth.logout();
      router.navigateByUrl('/login');
    }

    return throwError(() => erro);
    }),
  );
};
