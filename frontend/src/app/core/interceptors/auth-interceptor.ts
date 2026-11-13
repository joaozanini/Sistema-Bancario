import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { AuthService } from '../services/auth';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const paraGateway = req.url.startsWith(environment.apiUrl);
  const token = paraGateway ? auth.obterToken() : null;

  const request = token ?
  req.clone({
    setHeaders: {
      'x-access-token': token,
    },
  })
  : req;

  return next(request).pipe(
    catchError((erro: HttpErrorResponse) => {
    if(paraGateway && erro.status === 401) {
      auth.logout();
      router.navigateByUrl('/login');
    }

    return throwError(() => erro);
    }),
  );
};
