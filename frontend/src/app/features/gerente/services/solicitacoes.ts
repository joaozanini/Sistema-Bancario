import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

import { Solicitacao, SolicitacaoDto } from '../models/solicitacao.model';
import { ordenarSolicitacoes, paraSolicitacao } from '../utils/solicitacoes';

@Injectable({
  providedIn: 'root',
})
export class SolicitacoesService {
  private http = inject(HttpClient);

  private readonly solicitacoesUrl = 'http://localhost:3000/solicitacoes';

  listar(): Observable<Solicitacao[]> {
    return this.http
      .get<SolicitacaoDto[]>(this.solicitacoesUrl)
      .pipe(map((dtos) => dtos.map(paraSolicitacao).sort(ordenarSolicitacoes)));
  }

  // R9: o contrato final do aprovar vem com a task da SAGA
  aprovar(id: string): Observable<void> {
    return this.http
      .post<unknown>(`${this.solicitacoesUrl}/${id}/aprovar`, {})
      .pipe(map(() => undefined));
  }

  // R10: o contrato final do recusar vem com a task própria
  recusar(id: string, motivo: string): Observable<void> {
    return this.http
      .post<unknown>(`${this.solicitacoesUrl}/${id}/rejeitar`, { motivo })
      .pipe(map(() => undefined));
  }
}
