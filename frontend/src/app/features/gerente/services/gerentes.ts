import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable, of, switchMap, throwError } from 'rxjs';

import { JobAceito, JobsService } from '../../../core/services/jobs';
import { AtualizacaoGerente, GerenteDto } from '../models/gerente.model';

const MENSAGEM_REMOCAO = 'Gerente removido com sucesso.';

@Injectable({
  providedIn: 'root',
})
export class GerentesService {
  private http = inject(HttpClient);
  private jobsService = inject(JobsService);

  private readonly gerentesUrl = 'http://localhost:3000/gerentes';

  // R12: só os ativos, ordenados por nome
  listar(): Observable<GerenteDto[]> {
    return this.http
      .get<GerenteDto[]>(this.gerentesUrl)
      .pipe(
        map((gerentes) =>
          gerentes
            .filter((g) => g.ativo !== false)
            .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')),
        ),
      );
  }

  buscar(cpf: string): Observable<GerenteDto> {
    return this.http.get<GerenteDto>(`${this.gerentesUrl}/${cpf}`);
  }

  atualizar(cpf: string, dados: AtualizacaoGerente): Observable<GerenteDto> {
    return this.http.put<GerenteDto>(`${this.gerentesUrl}/${cpf}`, dados);
  }

  // R15 é SAGA: o Gateway responde 202 com o jobId e o resultado (inline) sai do polling do job
  remover(cpf: string): Observable<string> {
    return this.http
      .delete<JobAceito>(`${this.gerentesUrl}/${cpf}`, { observe: 'response' })
      .pipe(
        switchMap((resposta) => {
          const jobId = resposta.body?.jobId;
          if (resposta.status !== 202 || !jobId) {
            return of(MENSAGEM_REMOCAO);
          }

          return this.jobsService.aguardar(jobId).pipe(
            switchMap((job) => {
              if (job.status === 'FALHA') {
                return throwError(() => new Error(job.erro || 'Não foi possível remover o gerente.'));
              }
              if (job.resultType === 'inline') {
                return this.jobsService.resultado<unknown>(jobId).pipe(map(mensagemDoResultado));
              }
              return of(MENSAGEM_REMOCAO);
            }),
          );
        }),
      );
  }
}

// o enunciado só diz "mensagem de sucesso": aceita string ou { mensagem } / { message }
function mensagemDoResultado(resultado: unknown): string {
  if (typeof resultado === 'string' && resultado.trim()) {
    return resultado;
  }
  if (resultado && typeof resultado === 'object') {
    const { mensagem, message } = resultado as { mensagem?: string; message?: string };
    return mensagem || message || MENSAGEM_REMOCAO;
  }
  return MENSAGEM_REMOCAO;
}
