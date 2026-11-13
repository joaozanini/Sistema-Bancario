import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, of, switchMap, throwError } from 'rxjs';

import { JobAceito, JobsService } from '../../../core/services/jobs';
import { Solicitacao, SolicitacoesListDto } from '../models/solicitacao.model';
import { ordenarSolicitacoes, paraSolicitacao } from '../utils/solicitacoes';
import { environment } from '../../../../environments/environment';

const MENSAGEM_APROVACAO = 'Solicitação aprovada. A senha foi enviada por e-mail ao cliente.';

interface ClienteAprovado {
  nome: string;
}

@Injectable({
  providedIn: 'root',
})
export class SolicitacoesService {
  private http = inject(HttpClient);
  private jobsService = inject(JobsService);

  private readonly solicitacoesUrl = `${environment.apiUrl}/solicitacoes`;

  listar(): Observable<Solicitacao[]> {
    return this.http
      .get<SolicitacoesListDto>(this.solicitacoesUrl)
      .pipe(map(({ solicitacoes }) => solicitacoes.map(paraSolicitacao).sort(ordenarSolicitacoes)));
  }

  aprovar(cpf: string): Observable<string> {
    return this.http
      .post<JobAceito>(`${this.solicitacoesUrl}/${cpf}/aprovacao`, {}, { observe: 'response' })
      .pipe(
        switchMap((resposta) => {
          const jobId = resposta.body?.jobId;
          if (resposta.status !== 202 || !jobId) {
            return of(MENSAGEM_APROVACAO);
          }

          return this.jobsService.aguardar(jobId).pipe(
            switchMap((job) => {
              if (job.status === 'FALHA') {
                return throwError(() => new Error(job.erro || 'Não foi possível aprovar a solicitação.'));
              }
              if (job.resultType === 'resource' && job.resourceId) {
                return this.http
                  .get<ClienteAprovado>(`${environment.apiUrl}/${job.dominio}/${job.resourceId}`)
                  .pipe(
                    map((cliente) => `Cliente ${cliente.nome} aprovado. A senha foi enviada por e-mail.`),
                    catchError(() => of(MENSAGEM_APROVACAO)),
                  );
              }
              return of(MENSAGEM_APROVACAO);
            }),
          );
        }),
      );
  }

  recusar(cpf: string, motivo: string): Observable<void> {
    return this.http
      .post<unknown>(`${this.solicitacoesUrl}/${cpf}/rejeicao`, { motivo })
      .pipe(map(() => undefined));
  }
}
