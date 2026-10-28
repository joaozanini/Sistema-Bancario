import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Job, JobsService } from './jobs';

const URL = 'http://localhost:3000/jobs/j1/status';

function job(status: Job['status']): Job {
  return { jobId: 'j1', status, resultType: null, dominio: 'gerentes', resourceId: null, erro: null };
}

describe('JobsService', () => {
  let service: JobsService;
  let http: HttpTestingController;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(JobsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('consulta o status até o job sair de PENDENTE', () => {
    let final: Job | undefined;
    service.aguardar('j1').subscribe((j) => (final = j));

    vi.advanceTimersByTime(0);
    http.expectOne(URL).flush(job('PENDENTE'));
    expect(final).toBeUndefined();

    vi.advanceTimersByTime(1000);
    http.expectOne(URL).flush(job('CONCLUIDO'));
    expect(final?.status).toBe('CONCLUIDO');

    // parou de consultar
    vi.advanceTimersByTime(3000);
    http.expectNone(URL);
  });

  it('desiste depois do limite de tentativas', () => {
    let erro: Error | undefined;
    service.aguardar('j1').subscribe({ error: (e) => (erro = e) });

    for (let i = 0; i < 60; i++) {
      vi.advanceTimersByTime(i === 0 ? 0 : 1000);
      http.expectOne(URL).flush(job('PENDENTE'));
    }

    expect(erro?.message).toContain('demorando');
  });
});
