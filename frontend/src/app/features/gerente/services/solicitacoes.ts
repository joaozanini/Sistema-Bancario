import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { DateTime } from 'luxon';
import { delay, map, Observable, of } from 'rxjs';

import { Solicitacao, SolicitacaoDto } from '../models/solicitacao.model';
import { ordenarSolicitacoes, paraSolicitacao } from '../utils/solicitacoes';

// Enquanto não tem o GET no Back
const USAR_MOCK = true;

@Injectable({
  providedIn: 'root',
})
export class SolicitacoesService {
  private http = inject(HttpClient);

  private readonly solicitacoesUrl = 'http://localhost:3000/solicitacoes';

  private mock: SolicitacaoDto[] = solicitacoesMock();

  listar(): Observable<Solicitacao[]> {
    const dtos$ = USAR_MOCK
      ? of(structuredClone(this.mock)).pipe(delay(400))
      : this.http.get<SolicitacaoDto[]>(this.solicitacoesUrl);

    return dtos$.pipe(map((dtos) => dtos.map(paraSolicitacao).sort(ordenarSolicitacoes)));
  }

  aprovar(id: string): Observable<void> {
    if (USAR_MOCK) {
      this.atualizarMock(id, { status: 'APROVADO', motivoRejeicao: null });
      return of(undefined).pipe(delay(400));
    }

    return this.http
      .post<unknown>(`${this.solicitacoesUrl}/${id}/aprovar`, {})
      .pipe(map(() => undefined));
  }

  recusar(id: string, motivo: string): Observable<void> {
    if (USAR_MOCK) {
      this.atualizarMock(id, { status: 'NAO_APROVADO', motivoRejeicao: motivo });
      return of(undefined).pipe(delay(400));
    }

    return this.http
      .post<unknown>(`${this.solicitacoesUrl}/${id}/rejeitar`, { motivo })
      .pipe(map(() => undefined));
  }

  private atualizarMock(id: string, mudancas: Partial<SolicitacaoDto>): void {
    this.mock = this.mock.map((s) =>
      s.id === id ? { ...s, ...mudancas, dataAprovacaoRejeicao: DateTime.now().toISO() } : s,
    );
  }
}

function solicitacoesMock(): SolicitacaoDto[] {
  return [
    {
      id: 'b3f1c2a4-0001-4000-8000-000000000001',
      cpf: '12345678900',
      nome: 'Carlos Eduardo Silva',
      salario: '4200.0000',
      status: 'PENDENTE',
      motivoRejeicao: null,
      dataAprovacaoRejeicao: null,
    },
    {
      id: 'b3f1c2a4-0002-4000-8000-000000000002',
      cpf: '98765432199',
      nome: 'Mariana Costa Neves',
      salario: '3800.0000',
      status: 'APROVADO',
      motivoRejeicao: null,
      dataAprovacaoRejeicao: '2026-10-14T16:45:00-03:00',
    },
    {
      id: 'b3f1c2a4-0003-4000-8000-000000000003',
      cpf: '45678901233',
      nome: 'Lucas Arruda Mello',
      salario: '1900.0000',
      status: 'NAO_APROVADO',
      motivoRejeicao: 'Endereço não atende aos requisitos',
      dataAprovacaoRejeicao: '2026-10-13T11:30:00-03:00',
    },
  ];
}
