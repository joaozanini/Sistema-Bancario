import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable, of, switchMap, throwError } from 'rxjs';

import { JobAceito, JobsService } from '../../../core/services/jobs';
import { MoneyDecimal } from '../../../shared/utils/money.util';
import { Cliente, ClienteDto, LinhaRelatorio, LinhaRelatorioDto } from '../models/cliente.model';
import { environment } from '../../../../environments/environment';

export const URL_RELATORIO = `${environment.apiUrl}/relatorios/clientes`;

@Injectable({
  providedIn: 'root',
})
export class ClientesService {
  private http = inject(HttpClient);
  private jobsService = inject(JobsService);

  private readonly clientesUrl = `${environment.apiUrl}/clientes`;

  listar(): Observable<Cliente[]> {
    return this.http.get<ClienteDto[]>(this.clientesUrl).pipe(
      map((dtos) =>
        dtos
          .map((dto) => ({ ...dto, saldo: new MoneyDecimal(dto.saldo || '0') }))
          .sort(porNome),
      ),
    );
  }

  gerarRelatorio(): Observable<LinhaRelatorio[]> {
    return this.http
      .get<LinhaRelatorioDto[] | JobAceito>(URL_RELATORIO, { observe: 'response' })
      .pipe(
        switchMap((resposta) => {
          const corpo = resposta.body;
          if (resposta.status !== 202) {
            return of(Array.isArray(corpo) ? corpo : []);
          }

          const jobId = (corpo as JobAceito | null)?.jobId;
          if (!jobId) {
            return throwError(() => new Error('O servidor não retornou o identificador do relatório.'));
          }

          return this.jobsService.aguardar(jobId).pipe(
            switchMap((job) =>
              job.status === 'FALHA'
                ? throwError(() => new Error(job.erro || 'Não foi possível gerar o relatório.'))
                : this.jobsService.resultado<LinhaRelatorioDto[]>(jobId),
            ),
          );
        }),
        map((linhas) => linhas.map(paraLinhaRelatorio).sort(porNome)),
      );
  }
}

function paraLinhaRelatorio(dto: LinhaRelatorioDto): LinhaRelatorio {
  return {
    ...dto,
    salario: new MoneyDecimal(dto.salario || '0'),
    saldo: new MoneyDecimal(dto.saldo || '0'),
  };
}

function porNome(a: { nome: string }, b: { nome: string }): number {
  return a.nome.localeCompare(b.nome, 'pt-BR');
}
