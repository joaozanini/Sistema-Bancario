import { TestBed } from '@angular/core/testing';
import { provideRouter, Route, Router, UrlTree } from '@angular/router';

import { AuthService } from '../services/auth';
import { routes } from '../../app.routes';
import { gerenteGuard } from './gerente-guard';

describe('gerenteGuard', () => {
  let authService: { obterTipo: ReturnType<typeof vi.fn> };

  const executeGuard = () =>
    TestBed.runInInjectionContext(() => gerenteGuard({} as never, {} as never));

  const destinoDe = (resultado: unknown) =>
    TestBed.inject(Router).serializeUrl(resultado as UrlTree);

  beforeEach(() => {
    authService = { obterTipo: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService },
      ],
    });
  });

  it('permite o acesso de usuários GERENTE', () => {
    authService.obterTipo.mockReturnValue('GERENTE');

    expect(executeGuard()).toBe(true);
  });

  it('redireciona CLIENTE para sua página inicial', () => {
    authService.obterTipo.mockReturnValue('CLIENTE');

    expect(destinoDe(executeGuard())).toBe('/cliente');
  });

  it('redireciona para o login quando não há perfil', () => {
    authService.obterTipo.mockReturnValue(null);

    expect(destinoDe(executeGuard())).toBe('/login');
  });

  it('aplica a proteção de perfil a todas as rotas de gerente', () => {
    const rotasGerente = routes
      .flatMap((rota) => rota.children ?? [])
      .filter((rota: Route) => rota.path?.startsWith('gerente'));

    expect(rotasGerente.length).toBeGreaterThan(0);
    rotasGerente.forEach((rota) => expect(rota.canActivate).toContain(gerenteGuard));
  });
});
