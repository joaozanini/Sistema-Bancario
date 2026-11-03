import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { MoneyDecimal } from '../../../../shared/utils/money.util';
import { ContaService } from '../../services/conta';
import { ExtratoService } from '../../services/extrato';
import { TransferenciaService } from '../../services/transferencia';
import { Transferencia } from './transferencia';

describe('Transferencia', () => {
  let fixture: ComponentFixture<Transferencia>;
  let component: Transferencia;
  let extratoService: { obterExtrato: ReturnType<typeof vi.fn> };
  let transferenciaService: { transferir: ReturnType<typeof vi.fn> };
  let contaService: { obterResumo: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    extratoService = {
      obterExtrato: vi.fn().mockReturnValue(
        of({ numeroConta: '12345', saldoAbertura: '0', movimentacoes: [] }),
      ),
    };
    transferenciaService = {
      transferir: vi.fn().mockReturnValue(of({})),
    };
    contaService = {
      obterResumo: vi.fn().mockReturnValue(
        of({ numeroConta: '12345', saldo: new MoneyDecimal('850.00') }),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [Transferencia],
      providers: [
        provideRouter([]),
        { provide: ExtratoService, useValue: extratoService },
        { provide: TransferenciaService, useValue: transferenciaService },
        { provide: ContaService, useValue: contaService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Transferencia);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('exige o preenchimento da conta de destino', () => {
    component.valor = '10';

    component.continuar();

    expect(component.etapa()).toBe('dados');
    expect(component.erro()).toBe('Informe a conta de destino.');
    expect(transferenciaService.transferir).not.toHaveBeenCalled();
  });

  it('exige o preenchimento do valor', () => {
    component.contaDestino = '98765';

    component.continuar();

    expect(component.etapa()).toBe('dados');
    expect(component.erro()).toBe('Informe o valor da transferência.');
    expect(transferenciaService.transferir).not.toHaveBeenCalled();
  });

  it('rejeita valor zero', () => {
    component.contaDestino = '98765';
    component.valor = '0';

    component.continuar();

    expect(component.etapa()).toBe('dados');
    expect(component.erro()).toBe('O valor deve ser maior que zero.');
    expect(transferenciaService.transferir).not.toHaveBeenCalled();
  });

  it('bloqueia o envio quando não consegue carregar a conta de origem', () => {
    fixture.destroy();
    extratoService.obterExtrato.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0 })),
    );
    fixture = TestBed.createComponent(Transferencia);
    component = fixture.componentInstance;
    fixture.detectChanges();

    component.contaDestino = '98765';
    component.valor = '10';
    component.continuar();

    expect(component.contaOrigem()).toBeNull();
    expect(component.carregandoConta()).toBe(false);
    expect(component.erroConta()).toContain('Não foi possível carregar a conta de origem');
    expect(component.erro()).toContain('conta de origem');
    expect(transferenciaService.transferir).not.toHaveBeenCalled();
  });

  it('rejeita valores com mais de duas casas decimais', () => {
    component.contaDestino = '98765';
    component.valor = '10,123';

    component.continuar();

    expect(component.etapa()).toBe('dados');
    expect(component.erro()).toContain('duas casas decimais');
    expect(transferenciaService.transferir).not.toHaveBeenCalled();
  });

  it('não permite transferir para a própria conta', () => {
    component.contaDestino = '12.345';
    component.valor = '10';

    component.continuar();

    expect(component.etapa()).toBe('dados');
    expect(component.erro()).toContain('diferente da conta de origem');
  });

  it('mostra os dados para confirmação e normaliza o valor em centavos', () => {
    component.contaDestino = '98765';
    component.valor = '150,5';

    component.continuar();

    expect(component.etapa()).toBe('confirmacao');
    expect(component.valorNormalizado).toBe('150.50');
  });

  it('mostra a falha da API sem confirmar sucesso', () => {
    transferenciaService.transferir.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { message: 'Saldo insuficiente' },
          }),
      ),
    );
    component.contaDestino = '98765';
    component.valor = '150';
    component.continuar();

    component.confirmar();

    expect(component.etapa()).toBe('confirmacao');
    expect(component.erro()).toBe('Saldo insuficiente para realizar a transferência.');
  });

  it('informa quando a conta de destino não existe (HTTP 404)', () => {
    transferenciaService.transferir.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 404,
            error: { message: 'Conta não encontrada' },
          }),
      ),
    );
    component.contaDestino = '98765';
    component.valor = '150';
    component.continuar();

    component.confirmar();

    expect(component.etapa()).toBe('confirmacao');
    expect(component.erro()).toContain('Não foi possível localizar uma das contas');
  });

  it('informa quando o servidor está indisponível', () => {
    transferenciaService.transferir.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0 })),
    );
    component.contaDestino = '98765';
    component.valor = '150';
    component.continuar();

    component.confirmar();

    expect(component.etapa()).toBe('confirmacao');
    expect(component.erro()).toContain('Não foi possível conectar ao servidor');
  });

  it('impede envios duplicados enquanto a requisição está pendente', () => {
    const respostaPendente = new Subject<unknown>();
    transferenciaService.transferir.mockReturnValue(respostaPendente);
    component.contaDestino = '98765';
    component.valor = '150';
    component.continuar();

    component.confirmar();
    component.confirmar();

    expect(transferenciaService.transferir).toHaveBeenCalledTimes(1);
    expect(component.enviando()).toBe(true);

    respostaPendente.next({});
    respostaPendente.complete();

    expect(component.etapa()).toBe('sucesso');
  });

  it('reconsulta o saldo depois da transferência (5.10)', () => {
    component.contaDestino = '98765';
    component.valor = '150';
    component.continuar();

    component.confirmar();
    fixture.detectChanges();

    expect(component.etapa()).toBe('sucesso');
    expect(contaService.obterResumo).toHaveBeenCalledOnce();
    expect(component.saldoAtual()?.toFixed(2)).toBe('850.00');
    expect(fixture.nativeElement.textContent).toContain('Saldo atual');
  });
});
