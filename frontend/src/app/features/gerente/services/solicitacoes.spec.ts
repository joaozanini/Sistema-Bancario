import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { Job, JobsService } from '../../../core/services/jobs';
import { SolicitacoesService } from './solicitacoes';

const CPF = '12912861012';
const URL_APROVAR = `${environment.apiUrl}/solicitacoes/${CPF}/aprovar`;

function job(parcial: Partial<Job>): Job {
  return {
    jobId: 'j1',
    status: 'CONCLUIDO',
    resultType: 'resource',
    dominio: 'clientes',
    resourceId: CPF,
    erro: null,
    ...parcial,
  };
}

describe('SolicitacoesService', () => {
  let service: SolicitacoesService;
  let http: HttpTestingController;
  let jobsService: { aguardar: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    jobsService = { aguardar: vi.fn().mockReturnValue(of(job({}))) };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: JobsService, useValue: jobsService },
      ],
    });
    service = TestBed.inject(SolicitacoesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function aprovar(): { valor?: string; erro?: Error } {
    const saida: { valor?: string; erro?: Error } = {};
    service.aprovar(CPF).subscribe({
      next: (v) => (saida.valor = v),
      error: (e) => (saida.erro = e),
    });
    return saida;
  }

  it('aprova pelo CPF, faz polling do job e busca o cliente criado', () => {
    const saida = aprovar();

    const req = http.expectOne(URL_APROVAR);
    expect(req.request.method).toBe('POST');
    req.flush({ jobId: 'j1' }, { status: 202, statusText: 'Accepted' });

    expect(jobsService.aguardar).toHaveBeenCalledWith('j1');
    http.expectOne(`${environment.apiUrl}/clientes/${CPF}`).flush({ nome: 'Catharyna' });

    expect(saida.valor).toBe('Cliente Catharyna aprovado. A senha foi enviada por e-mail.');
  });

  it('job em FALHA vira erro com a mensagem do job', () => {
    jobsService.aguardar.mockReturnValue(
      of(job({ status: 'FALHA', resultType: null, resourceId: null, erro: 'E-mail já cadastrado' })),
    );
    const saida = aprovar();

    http.expectOne(URL_APROVAR).flush({ jobId: 'j1' }, { status: 202, statusText: 'Accepted' });

    expect(saida.erro?.message).toBe('E-mail já cadastrado');
  });

  it('se não conseguir buscar o cliente, conclui com a mensagem padrão', () => {
    const saida = aprovar();

    http.expectOne(URL_APROVAR).flush({ jobId: 'j1' }, { status: 202, statusText: 'Accepted' });
    http
      .expectOne(`${environment.apiUrl}/clientes/${CPF}`)
      .flush(null, { status: 502, statusText: 'Bad Gateway' });

    expect(saida.valor).toBe('Solicitação aprovada. A senha foi enviada por e-mail ao cliente.');
  });

  it('resposta síncrona (sem 202) conclui sem polling', () => {
    const saida = aprovar();

    http.expectOne(URL_APROVAR).flush({}, { status: 200, statusText: 'OK' });

    expect(jobsService.aguardar).not.toHaveBeenCalled();
    expect(saida.valor).toBe('Solicitação aprovada. A senha foi enviada por e-mail ao cliente.');
  });
});
