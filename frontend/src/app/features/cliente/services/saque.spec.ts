import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { SaqueService } from './saque';

describe('SaqueService', () => {
  let service: SaqueService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SaqueService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envia POST para /contas/{numero}/saque com o valor em string', () => {
    service.sacar('1291', '150.50').subscribe();

    const req = http.expectOne(`${environment.apiUrl}/contas/1291/saque`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ valor: '150.50' });
    req.flush({});
  });
});
