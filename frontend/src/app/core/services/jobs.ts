import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, concatMap, EmptyError, first, Observable, take, throwError, timer } from 'rxjs';
import { environment } from '../../../environments/environment';

export type StatusJob = 'PENDENTE' | 'CONCLUIDO' | 'FALHA';

export interface Job {
  jobId: string;
  status: StatusJob;
  resultType: 'resource' | 'inline' | null;
  dominio: string;
  resourceId: string | null;
  erro: string | null;
}

export interface JobAceito {
  jobId: string;
}

const INTERVALO_MS = 1000;
const MAX_TENTATIVAS = 60;

@Injectable({
  providedIn: 'root',
})
export class JobsService {
  private http = inject(HttpClient);

  private readonly jobsUrl = `${environment.apiUrl}/jobs`;

  aguardar(jobId: string): Observable<Job> {
    return timer(0, INTERVALO_MS).pipe(
      take(MAX_TENTATIVAS),
      concatMap(() => this.http.get<Job>(`${this.jobsUrl}/${jobId}/status`)),
      first((job) => job.status !== 'PENDENTE'),
      catchError((err) =>
        throwError(() =>
          err instanceof EmptyError
            ? new Error('A operação está demorando mais que o esperado. Atualize a página em instantes.')
            : err,
        ),
      ),
    );
  }

  resultado<T>(jobId: string): Observable<T> {
    return this.http.get<T>(`${this.jobsUrl}/${jobId}/result`);
  }
}
