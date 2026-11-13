import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { CepNaoEncontradoError, CepService, Endereco } from './cep';

describe('CepService', () => {
  let service: CepService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CepService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta o CEP no API Gateway só com os dígitos e devolve o endereço', () => {
    let endereco: Endereco | undefined;
    service.buscar('80060-000').subscribe((e) => (endereco = e));

    http.expectOne(`${environment.apiUrl}/cep/80060000`).flush({
      cep: '80060000',
      logradouro: 'Rua XV de Novembro',
      complemento: 'até 1299/1300',
      cidade: 'Curitiba',
      uf: 'PR',
      _links: { self: { href: `${environment.apiUrl}/cep/80060000` } },
    });

    expect(endereco).toEqual({
      logradouro: 'Rua XV de Novembro',
      complemento: 'até 1299/1300',
      cidade: 'Curitiba',
      uf: 'PR',
    });
  });

  it('404 do Gateway vira CepNaoEncontradoError', () => {
    let erro: unknown;
    service.buscar('99999999').subscribe({ error: (e) => (erro = e) });

    http
      .expectOne(`${environment.apiUrl}/cep/99999999`)
      .flush({ status: 404, erro: 'Not Found', mensagem: 'CEP não encontrado.' }, { status: 404, statusText: 'Not Found' });

    expect(erro).toBeInstanceOf(CepNaoEncontradoError);
  });

  it('outro erro (consulta indisponível) não é tratado como CEP inexistente', () => {
    let erro: unknown;
    service.buscar('80060000').subscribe({ error: (e) => (erro = e) });

    http
      .expectOne(`${environment.apiUrl}/cep/80060000`)
      .flush({ status: 502, erro: 'Bad Gateway', mensagem: 'Não foi possível consultar o CEP.' }, { status: 502, statusText: 'Bad Gateway' });

    expect(erro).not.toBeInstanceOf(CepNaoEncontradoError);
  });
});
