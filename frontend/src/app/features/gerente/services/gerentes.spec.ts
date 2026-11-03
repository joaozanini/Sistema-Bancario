import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { Job, JobsService } from '../../../core/services/jobs';
import { GerentesService } from './gerentes';

const URL = 'http://localhost:3000/gerentes';

function job(parcial: Partial<Job>): Job {
  return { jobId: 'j1', status: 'CONCLUIDO', resultType: 'inline', dominio: 'gerentes', resourceId: null, erro: null, ...parcial };
}

describe('GerentesService', () => {
  let service: GerentesService;
  let http: HttpTestingController;
  let jobsService: { aguardar: ReturnType<typeof vi.fn>; resultado: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    jobsService = {
      aguardar: vi.fn().mockReturnValue(of(job({}))),
      resultado: vi.fn().mockReturnValue(of({ mensagem: 'Gerente Godophredo removido.' })),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: JobsService, useValue: jobsService },
      ],
    });
    service = TestBed.inject(GerentesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function remover(): { valor?: string; erro?: Error } {
    const saida: { valor?: string; erro?: Error } = {};
    service.remover('64065268052').subscribe({
      next: (v) => (saida.valor = v),
      error: (e) => (saida.erro = e),
    });
    return saida;
  }

  it('lista só os gerentes ativos, ordenados por nome', () => {
    let nomes: string[] = [];
    service.listar().subscribe((lista) => (nomes = lista.map((g) => g.nome)));

    http.expectOne(URL).flush([
      { cpf: '2', nome: 'Godophredo', email: '', telefone: null, ativo: true },
      { cpf: '3', nome: 'Gyândula', email: '', telefone: null, ativo: false },
      { cpf: '1', nome: 'Geniéve', email: '', telefone: null, ativo: true },
    ]);

    expect(nomes).toEqual(['Geniéve', 'Godophredo']);
  });

  it('no 202 faz polling do job e busca a mensagem inline', () => {
    const saida = remover();

    const req = http.expectOne(`${URL}/64065268052`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ jobId: 'j1' }, { status: 202, statusText: 'Accepted' });

    expect(jobsService.aguardar).toHaveBeenCalledWith('j1');
    expect(jobsService.resultado).toHaveBeenCalledWith('j1');
    expect(saida.valor).toBe('Gerente Godophredo removido.');
  });

  it('job em FALHA vira erro com a mensagem do job', () => {
    jobsService.aguardar.mockReturnValue(
      of(job({ status: 'FALHA', resultType: null, erro: 'Não é permitido remover o último gerente ativo.' })),
    );
    const saida = remover();

    http.expectOne(`${URL}/64065268052`).flush({ jobId: 'j1' }, { status: 202, statusText: 'Accepted' });

    expect(saida.erro?.message).toBe('Não é permitido remover o último gerente ativo.');
    expect(jobsService.resultado).not.toHaveBeenCalled();
  });

  it('resposta síncrona (sem 202) conclui sem polling', () => {
    const saida = remover();

    http.expectOne(`${URL}/64065268052`).flush(null, { status: 204, statusText: 'No Content' });

    expect(jobsService.aguardar).not.toHaveBeenCalled();
    expect(saida.valor).toBe('Gerente removido com sucesso.');
  });
});
