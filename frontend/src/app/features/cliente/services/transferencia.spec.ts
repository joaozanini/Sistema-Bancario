import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { TransferenciaService } from './transferencia';

describe('TransferenciaService', () => {
  let service: TransferenciaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TransferenciaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('envia somente o destino e o valor decimal para a conta de origem', () => {
    service.transferir('12345-6', '98765', '150.50').subscribe();

    const requisicao = http.expectOne(
      'http://localhost:3000/contas/12345-6/transferencia',
    );
    expect(requisicao.request.method).toBe('POST');
    expect(requisicao.request.body).toEqual({
      contaDestino: '98765',
      valor: '150.50',
    });
    requisicao.flush({});
  });
});
