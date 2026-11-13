import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { DateTime } from 'luxon';
import { Observable, switchMap } from 'rxjs';

import { ExtratoDto } from '../models/extrato.model';
import { environment } from '../../../../environments/environment';
import { ContaService } from './conta';

@Injectable({
  providedIn: 'root',
})
export class ExtratoService {
  private http = inject(HttpClient);
  private contaService = inject(ContaService);

  private readonly contasUrl = `${environment.apiUrl}/contas`;

  /** R7: extrato da conta do cliente logado, em GET /contas/{numero}/extrato. */
  obterExtrato(inicio: DateTime, fim: DateTime): Observable<ExtratoDto> {
    const params = new HttpParams()
      .set('inicio', inicio.toISODate() ?? '')
      .set('fim', fim.toISODate() ?? '');

    return this.contaService
      .obterNumeroConta()
      .pipe(
        switchMap((numero) =>
          this.http.get<ExtratoDto>(`${this.contasUrl}/${encodeURIComponent(numero)}/extrato`, { params }),
        ),
      );
  }
}
