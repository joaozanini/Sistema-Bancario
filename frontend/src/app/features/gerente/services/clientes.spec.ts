import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { Job, JobsService } from '../../../core/services/jobs';
import { LinhaRelatorio, LinhaRelatorioDto } from '../models/cliente.model';
import { ClientesService, URL_RELATORIO } from './clientes';

function job(parcial: Partial<Job>): Job {
  return {
    jobId: 'j1',
    status: 'CONCLUIDO',
    resultType: 'inline',
    dominio: 'clientes',
    resourceId: null,
    erro: null,
    ...parcial,
  };
}

const LINHAS: LinhaRelatorioDto[] = [
  {
    cpf: '09506382000',
    nome: 'Cleuddônio',
    email: 'cli2@bantads.com.br',
    salario: '20000.0000',
    numeroConta: '0950',
    saldo: '-10000.0000',
    cpfGerente: '64065268052',
    nomeGerente: 'Godophredo',
  },
  {
    cpf: '12912861012',
    nome: 'Catharyna',
    email: 'cli1@bantads.com.br',
    salario: '10000.0000',
    numeroConta: '1291',
    saldo: '800.0000',
    cpfGerente: '98574307084',
    nomeGerente: 'Geniéve',
  },
];

describe('ClientesService', () => {
  let service: ClientesService;
  let http: HttpTestingController;
  let jobsService: { aguardar: ReturnType<typeof vi.fn>; resultado: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    jobsService = {
      aguardar: vi.fn().mockReturnValue(of(job({}))),
      resultado: vi.fn().mockReturnValue(of({ clientes: LINHAS })),
    };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: JobsService, useValue: jobsService },
      ],
    });
    service = TestBed.inject(ClientesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function gerar(): { linhas?: LinhaRelatorio[]; erro?: Error } {
    const saida: { linhas?: LinhaRelatorio[]; erro?: Error } = {};
    service.gerarRelatorio().subscribe({
      next: (l) => (saida.linhas = l),
      error: (e) => (saida.erro = e),
    });
    return saida;
  }

  it('lista os clientes por nome com saldo em Decimal', () => {
    let resultado: { nome: string; saldo: string }[] = [];
    service
      .listar()
      .subscribe((lista) => (resultado = lista.map((c) => ({ nome: c.nome, saldo: c.saldo.toFixed(2) }))));

    // a lista vem dentro de { clientes }, com o estado no campo "estado"
    http.expectOne(`${environment.apiUrl}/clientes`).flush({
      clientes: [
        { cpf: '2', nome: 'Cleuddônio', cidade: 'Curitiba', estado: 'PR', saldo: '-10000.00' },
        { cpf: '1', nome: 'Catharyna', cidade: 'Curitiba', estado: 'PR', saldo: '800.00' },
      ],
      _links: { self: { href: `${environment.apiUrl}/clientes` } },
    });

    expect(resultado).toEqual([
      { nome: 'Catharyna', saldo: '800.00' },
      { nome: 'Cleuddônio', saldo: '-10000.00' },
    ]);
  });

  it('no 202 faz polling e busca o relatório inline em /jobs/{id}/result', () => {
    const saida = gerar();

    http.expectOne(URL_RELATORIO).flush({ jobId: 'j1' }, { status: 202, statusText: 'Accepted' });

    expect(jobsService.aguardar).toHaveBeenCalledWith('j1');
    expect(jobsService.resultado).toHaveBeenCalledWith('j1');
    expect(saida.linhas?.map((l) => l.nome)).toEqual(['Catharyna', 'Cleuddônio']);
    expect(saida.linhas?.[0].saldo.toFixed(2)).toBe('800.00');
  });

  it('job em FALHA vira erro com a mensagem do job', () => {
    jobsService.aguardar.mockReturnValue(of(job({ status: 'FALHA', erro: 'MS Conta indisponível' })));
    const saida = gerar();

    http.expectOne(URL_RELATORIO).flush({ jobId: 'j1' }, { status: 202, statusText: 'Accepted' });

    expect(saida.erro?.message).toBe('MS Conta indisponível');
    expect(jobsService.resultado).not.toHaveBeenCalled();
  });

  it('resposta síncrona (200) usa o relatório do corpo, sem polling', () => {
    const saida = gerar();

    http.expectOne(URL_RELATORIO).flush({ clientes: LINHAS });

    expect(jobsService.aguardar).not.toHaveBeenCalled();
    expect(saida.linhas?.length).toBe(2);
  });
});
