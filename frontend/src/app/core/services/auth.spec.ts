import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth';

const LOGOUT_URL = `${environment.apiUrl}/auth/logout`;

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);

    service.salvarToken('jwt');
    service.salvarTipo('CLIENTE');
    service.salvarUsuario({ cpf: '12912861012', nome: 'Catharyna', email: 'cli1@bantads.com.br' });
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
  });

  it('sair chama o logout do Gateway e limpa a sessão local', () => {
    let concluiu = false;
    service.sair().subscribe({ complete: () => (concluiu = true) });

    const req = http.expectOne(LOGOUT_URL);
    expect(req.request.method).toBe('POST');
    req.flush({ auth: false });

    expect(concluiu).toBe(true);
    expect(service.obterToken()).toBeNull();
    expect(service.obterUsuario()).toBeNull();
    expect(service.obterTipo()).toBeNull();
  });

  it('limpa a sessão local mesmo se o Gateway falhar', () => {
    let concluiu = false;
    service.sair().subscribe({ complete: () => (concluiu = true) });

    http.expectOne(LOGOUT_URL).flush(null, { status: 503, statusText: 'Unavailable' });

    expect(concluiu).toBe(true);
    expect(service.estaAutenticado()).toBe(false);
  });

  it('não aceita mais o perfil ADMIN', () => {
    localStorage.setItem('tipo', 'ADMIN');
    expect(service.obterTipo()).toBeNull();
  });
});
