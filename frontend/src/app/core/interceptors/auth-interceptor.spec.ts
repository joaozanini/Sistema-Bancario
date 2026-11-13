import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';

import { AuthService } from '../services/auth';
import { authInterceptor } from './auth-interceptor';

describe('authInterceptor', () => {
  let authService: { obterToken: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn> };
  let router: { navigateByUrl: ReturnType<typeof vi.fn> };

  const interceptor: HttpInterceptorFn = (req, next) =>
    TestBed.runInInjectionContext(() => authInterceptor(req, next));

  beforeEach(() => {
    authService = {
      obterToken: vi.fn().mockReturnValue('token'),
      logout: vi.fn(),
    };
    router = { navigateByUrl: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('should be created', () => {
    expect(interceptor).toBeTruthy();
  });

  it('encerra a sessão e redireciona ao login quando recebe HTTP 401', () => {
    const request = new HttpRequest('GET', `${environment.apiUrl}/contas/extrato`);
    const response = interceptor(request, () =>
      throwError(() => new HttpErrorResponse({ status: 401 })),
    );

    response.subscribe({ error: () => undefined });

    expect(authService.logout).toHaveBeenCalledOnce();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('envia o token só para o Gateway', () => {
    let enviadaAoGateway: HttpRequest<unknown> | undefined;
    let enviadaAoViaCep: HttpRequest<unknown> | undefined;

    interceptor(new HttpRequest('GET', `${environment.apiUrl}/clientes`), (req) => {
      enviadaAoGateway = req;
      return of(new HttpResponse());
    }).subscribe();
    interceptor(new HttpRequest('GET', 'https://viacep.com.br/ws/80060000/json/'), (req) => {
      enviadaAoViaCep = req;
      return of(new HttpResponse());
    }).subscribe();

    expect(enviadaAoGateway?.headers.get('x-access-token')).toBe('token');
    expect(enviadaAoViaCep?.headers.has('x-access-token')).toBe(false);
  });

  it('não encerra a sessão por um 401 de fora do Gateway', () => {
    interceptor(new HttpRequest('GET', 'https://viacep.com.br/ws/80060000/json/'), () =>
      throwError(() => new HttpErrorResponse({ status: 401 })),
    ).subscribe({ error: () => undefined });

    expect(authService.logout).not.toHaveBeenCalled();
  });
});
