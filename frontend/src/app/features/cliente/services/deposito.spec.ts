import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { DepositoService } from './deposito';

describe('DepositoService', () => {
  let service: DepositoService;
  let httpController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DepositoService);
    httpController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpController.verify());

  it('envia POST para o caminho previsto com valor decimal', () => {
    let resposta: unknown;
    service.depositar('12345', '150.50').subscribe((resultado) => (resposta = resultado));

    const requisicao = httpController.expectOne('http://localhost:3000/contas/12345/deposito');
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual({ valor: '150.50' });

    requisicao.flush({ mensagem: 'Depósito confirmado' });
    expect(resposta).toEqual({ mensagem: 'Depósito confirmado' });
  });
});
