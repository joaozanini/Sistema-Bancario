import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CepNaoEncontradoError, CepService, Endereco, URL_VIACEP } from './cep';

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

  it('consulta o ViaCEP só com os dígitos e devolve o endereço', () => {
    let endereco: Endereco | undefined;
    service.buscar('80060-000').subscribe((e) => (endereco = e));

    http.expectOne(`${URL_VIACEP}/80060000/json/`).flush({
      logradouro: 'Rua XV de Novembro',
      complemento: 'até 1299/1300',
      localidade: 'Curitiba',
      uf: 'PR',
    });

    expect(endereco).toEqual({
      logradouro: 'Rua XV de Novembro',
      complemento: 'até 1299/1300',
      cidade: 'Curitiba',
      uf: 'PR',
    });
  });

  it('erro: true do ViaCEP vira CepNaoEncontradoError', () => {
    let erro: unknown;
    service.buscar('99999999').subscribe({ error: (e) => (erro = e) });

    http.expectOne(`${URL_VIACEP}/99999999/json/`).flush({ erro: 'true' });

    expect(erro).toBeInstanceOf(CepNaoEncontradoError);
  });
});
