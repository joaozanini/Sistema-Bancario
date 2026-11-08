import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';

import { AuthService } from '../services/auth';
import { visitanteGuard } from './visitante-guard';

describe('visitanteGuard', () => {
  let authService: {
    estaAutenticado: ReturnType<typeof vi.fn>;
    obterTipo: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  const executeGuard = () =>
    TestBed.runInInjectionContext(() => visitanteGuard({} as never, {} as never));

  const destinoDe = (resultado: unknown) =>
    TestBed.inject(Router).serializeUrl(resultado as UrlTree);

  beforeEach(() => {
    authService = { estaAutenticado: vi.fn(), obterTipo: vi.fn(), logout: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService },
      ],
    });
  });

  it('permite o acesso de quem não está logado', () => {
    authService.estaAutenticado.mockReturnValue(false);

    expect(executeGuard()).toBe(true);
  });

  it('manda o CLIENTE logado para sua página inicial', () => {
    authService.estaAutenticado.mockReturnValue(true);
    authService.obterTipo.mockReturnValue('CLIENTE');

    expect(destinoDe(executeGuard())).toBe('/cliente');
  });

  it('manda o GERENTE logado para sua página inicial', () => {
    authService.estaAutenticado.mockReturnValue(true);
    authService.obterTipo.mockReturnValue('GERENTE');

    expect(destinoDe(executeGuard())).toBe('/gerente');
  });

  it('limpa a sessão inconsistente (token sem perfil) e libera o login', () => {
    authService.estaAutenticado.mockReturnValue(true);
    authService.obterTipo.mockReturnValue(null);

    expect(executeGuard()).toBe(true);
    expect(authService.logout).toHaveBeenCalled();
  });
});
