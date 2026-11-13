import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from '../../../core/services/auth';
import { environment } from '../../../../environments/environment';
import { ContaService } from './conta';

const CPF = '12912861012';
const URL_CONTA = `${environment.apiUrl}/clientes/${CPF}/conta`;

const CONTA = {
  numero: '1291',
  cpfCliente: CPF,
  cpfGerente: '98574307084',
  saldo: '800.00',
  dataCriacao: '2000-01-01',
};

describe('ContaService', () => {
  let service: ContaService;
  let http: HttpTestingController;
  let usuario: { cpf: string } | null;

  beforeEach(() => {
    usuario = { cpf: CPF };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { obterUsuario: () => usuario, obterToken: () => null } },
      ],
    });
    service = TestBed.inject(ContaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lê o número da conta e o saldo atual em GET /clientes/{cpf}/conta (R3)', () => {
    let resultado: { numeroConta: string; saldo: string } | undefined;
    service
      .obterResumo()
      .subscribe((c) => (resultado = { numeroConta: c.numeroConta, saldo: c.saldo.toFixed(2) }));

    const req = http.expectOne(URL_CONTA);
    expect(req.request.method).toBe('GET');
    req.flush(CONTA);

    expect(resultado).toEqual({ numeroConta: '1291', saldo: '800.00' });
  });

  it('reconsulta o saldo a cada chamada, sem guardar valor antigo', () => {
    const saldos: string[] = [];
    const ler = () => service.obterResumo().subscribe((c) => saldos.push(c.saldo.toFixed(2)));

    ler();
    http.expectOne(URL_CONTA).flush(CONTA);
    ler();
    http.expectOne(URL_CONTA).flush({ ...CONTA, saldo: '950.00' });

    expect(saldos).toEqual(['800.00', '950.00']);
  });

  it('guarda só o número da conta, para o extrato não consultá-lo de novo', () => {
    const numeros: string[] = [];

    service.obterNumeroConta().subscribe((n) => numeros.push(n));
    http.expectOne(URL_CONTA).flush(CONTA);

    service.obterNumeroConta().subscribe((n) => numeros.push(n));
    http.expectNone(URL_CONTA);

    expect(numeros).toEqual(['1291', '1291']);
  });

  it('não reaproveita o número da conta de outro usuário', () => {
    service.obterNumeroConta().subscribe();
    http.expectOne(URL_CONTA).flush(CONTA);

    usuario = { cpf: '09506382000' };
    let numero = '';
    service.obterNumeroConta().subscribe((n) => (numero = n));
    http
      .expectOne(`${environment.apiUrl}/clientes/09506382000/conta`)
      .flush({ ...CONTA, numero: '0950', cpfCliente: '09506382000' });

    expect(numero).toBe('0950');
  });

  it('falha sem chamar a API quando não há usuário logado', () => {
    usuario = null;
    let erro: Error | undefined;
    service.obterResumo().subscribe({ error: (e) => (erro = e) });

    http.expectNone(URL_CONTA);
    expect(erro?.message).toContain('cliente logado');
  });
});
