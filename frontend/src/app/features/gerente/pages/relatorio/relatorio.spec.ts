import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, Subject, throwError } from 'rxjs';

import { MoneyDecimal } from '../../../../shared/utils/money.util';
import { LinhaRelatorio } from '../../models/cliente.model';
import { ClientesService } from '../../services/clientes';
import { Relatorio } from './relatorio';

const LINHA: LinhaRelatorio = {
  cpf: '12912861012',
  nome: 'Catharyna',
  email: 'cli1@bantads.com.br',
  salario: new MoneyDecimal('10000'),
  numeroConta: '1291',
  saldo: new MoneyDecimal('800'),
  cpfGerente: '98574307084',
  nomeGerente: 'Geniéve',
};

describe('Relatorio', () => {
  let fixture: ComponentFixture<Relatorio>;
  let clientesService: { gerarRelatorio: ReturnType<typeof vi.fn> };

  async function criar() {
    await TestBed.configureTestingModule({
      imports: [Relatorio],
      providers: [{ provide: ClientesService, useValue: clientesService }],
    }).compileComponents();

    fixture = TestBed.createComponent(Relatorio);
    fixture.detectChanges();
  }

  it('mostra que está gerando enquanto o job não termina', async () => {
    clientesService = { gerarRelatorio: vi.fn().mockReturnValue(new Subject()) };
    await criar();

    expect(fixture.nativeElement.textContent).toContain('Gerando relatório');
  });

  it('mostra os oito campos do relatório', async () => {
    clientesService = { gerarRelatorio: vi.fn().mockReturnValue(of([LINHA])) };
    await criar();

    const celulas = Array.from(
      fixture.nativeElement.querySelectorAll('tbody td') as NodeListOf<HTMLElement>,
    ).map((td) => td.textContent?.trim());
    expect(celulas).toEqual([
      '129.128.610-12',
      'Catharyna',
      'cli1@bantads.com.br',
      'R$ 10.000,00',
      '1291',
      'R$ 800,00',
      '985.743.070-84',
      'Geniéve',
    ]);
  });

  it('mostra o erro do job em FALHA e permite gerar de novo', async () => {
    clientesService = {
      gerarRelatorio: vi
        .fn()
        .mockReturnValueOnce(throwError(() => new Error('MS Conta indisponível')))
        .mockReturnValue(of([LINHA])),
    };
    await criar();

    expect(fixture.nativeElement.textContent).toContain('MS Conta indisponível');

    fixture.componentInstance.gerar();
    fixture.detectChanges();

    expect(clientesService.gerarRelatorio).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });
});
