import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { DateTime } from 'luxon';
import { Observable } from 'rxjs';

import { ExtratoDto } from '../models/extrato.model';
import { environment } from '../../../../environments/environment';

@Injectable({
  providedIn: 'root',
})
export class ExtratoService {
  private http = inject(HttpClient);

  private readonly extratoUrl = `${environment.apiUrl}/contas/extrato`;

  obterExtrato(inicio: DateTime, fim: DateTime): Observable<ExtratoDto> {
    const params = new HttpParams()
      .set('inicio', inicio.toISODate() ?? '')
      .set('fim', fim.toISODate() ?? '');

    return this.http.get<ExtratoDto>(this.extratoUrl, { params });
  }
}
