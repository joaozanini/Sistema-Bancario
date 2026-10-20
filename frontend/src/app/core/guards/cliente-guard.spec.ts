import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';

import { AuthService } from '../services/auth';
import { routes } from '../../app.routes';
import { clienteGuard } from './cliente-guard';

describe('clienteGuard', () => {
  let authService: { obterTipo: ReturnType<typeof vi.fn> };

  const executeGuard = () =>
    TestBed.runInInjectionContext(() => clienteGuard({} as never, {} as never));

  beforeEach(() => {
    authService = { obterTipo: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService },
      ],
    });
  });

  it('permite o acesso de usuários CLIENTE', () => {
    authService.obterTipo.mockReturnValue('CLIENTE');

    expect(executeGuard()).toBe(true);
  });

  it('redireciona GERENTE para sua página inicial', () => {
    authService.obterTipo.mockReturnValue('GERENTE');

    const resultado = executeGuard() as UrlTree;
    const router = TestBed.inject(Router);

    expect(router.serializeUrl(resultado)).toBe('/gerente');
  });

  it('aplica a proteção de perfil à rota de transferência', () => {
    const rotaTransferencia = routes
      .flatMap((rota) => rota.children ?? [])
      .find((rota) => rota.path === 'transferencia');

    expect(rotaTransferencia?.canActivate).toContain(clienteGuard);
  });
});
