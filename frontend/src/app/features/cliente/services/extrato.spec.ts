import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DateTime } from 'luxon';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { ExtratoDto } from '../models/extrato.model';
import { ContaService } from './conta';
import { ExtratoService } from './extrato';

describe('ExtratoService', () => {
  it('consulta o extrato em GET /contas/{numero}/extrato com o período informado (R7)', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ContaService, useValue: { obterNumeroConta: () => of('0950') } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);

    let extrato: ExtratoDto | undefined;
    TestBed.inject(ExtratoService)
      .obterExtrato(DateTime.fromISO('2025-01-01'), DateTime.fromISO('2025-01-31'))
      .subscribe((e) => (extrato = e));

    const req = http.expectOne(
      (r) => r.url === `${environment.apiUrl}/contas/0950/extrato`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('inicio')).toBe('2025-01-01');
    expect(req.request.params.get('fim')).toBe('2025-01-31');
    req.flush({ numeroConta: '0950', saldoAbertura: '1700.00', movimentacoes: [] });

    expect(extrato?.saldoAbertura).toBe('1700.00');
    http.verify();
  });
});
