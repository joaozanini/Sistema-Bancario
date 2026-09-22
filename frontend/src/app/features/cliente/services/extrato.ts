import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { DateTime } from 'luxon';
import { delay, Observable, of } from 'rxjs';

import { ExtratoDto } from '../models/extrato.model';

const USAR_MOCK = false;

@Injectable({
  providedIn: 'root',
})
export class ExtratoService {
  private http = inject(HttpClient);

  private readonly extratoUrl = 'http://localhost:3000/contas/extrato';

  obterExtrato(inicio: DateTime, fim: DateTime): Observable<ExtratoDto> {
    if (USAR_MOCK) {
      return of(extratoMock(inicio)).pipe(delay(400));
    }

    const params = new HttpParams()
      .set('inicio', inicio.toISODate() ?? '')
      .set('fim', fim.toISODate() ?? '');

    return this.http.get<ExtratoDto>(this.extratoUrl, { params });
  }
}

function extratoMock(inicio: DateTime): ExtratoDto {
  const em = (dias: number, hora: string) =>
    `${inicio.plus({ days: dias }).toISODate()}T${hora}:00-03:00`;

  return {
    numeroConta: '1291',
    saldoAbertura: '800.0000',
    movimentacoes: [
      { dataHora: em(1, '09:15'), tipo: 'DEPOSITO', valor: '1000.0000' },
      { dataHora: em(1, '14:40'), tipo: 'SAQUE', valor: '200.0000' },
      {
        dataHora: em(4, '10:05'),
        tipo: 'TRANSFERENCIA',
        cpfOrigem: '12912861012',
        nomeOrigem: 'Catharyna',
        cpfDestino: '09506382000',
        nomeDestino: 'Cleuddônio',
        valor: '150.5000',
      },
      {
        dataHora: em(7, '16:20'),
        tipo: 'TRANSFERENCIA',
        cpfOrigem: '85733854057',
        nomeOrigem: 'Catianna',
        cpfDestino: '12912861012',
        nomeDestino: 'Catharyna',
        valor: '75.2500',
      },
    ],
    _links: {},
  };
}