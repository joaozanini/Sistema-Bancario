import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../../environments/environment';
import { AutocadastroService, SolicitacaoCadastroRequest } from './autocadastro';

const FORMULARIO: SolicitacaoCadastroRequest = {
  nome: 'Fulano de Tal',
  cpf: '11122233396',
  email: 'fulano@exemplo.com.br',
  telefone: '(41) 99999-0000',
  salario: '4500.00',
  logradouro: 'Rua XV de Novembro',
  numero: '1299',
  complemento: '',
  cep: '80060-000',
  cidade: 'Curitiba',
  uf: 'PR',
};

describe('AutocadastroService', () => {
  let service: AutocadastroService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AutocadastroService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envia o autocadastro com o endereço aninhado e o CEP só com dígitos (R1)', () => {
    let status = '';
    service.solicitar(FORMULARIO).subscribe((solicitacao) => (status = solicitacao.status));

    const req = http.expectOne(`${environment.apiUrl}/solicitacoes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      cpf: '11122233396',
      nome: 'Fulano de Tal',
      email: 'fulano@exemplo.com.br',
      telefone: '(41) 99999-0000',
      salario: '4500.00',
      endereco: {
        logradouro: 'Rua XV de Novembro',
        numero: '1299',
        complemento: null,
        cep: '80060000',
        cidade: 'Curitiba',
        uf: 'PR',
      },
    });
    req.flush({ ...req.request.body, status: 'PENDENTE', motivo: null, dataHoraProcessamento: null });

    expect(status).toBe('PENDENTE');
  });

  it('mantém o complemento quando ele é informado', () => {
    service.solicitar({ ...FORMULARIO, complemento: ' Ap. 42 ' }).subscribe();

    const req = http.expectOne(`${environment.apiUrl}/solicitacoes`);
    expect(req.request.body.endereco.complemento).toBe('Ap. 42');
    req.flush({});
  });
});
