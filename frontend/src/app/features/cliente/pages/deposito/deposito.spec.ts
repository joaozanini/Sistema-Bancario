import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { AuthService } from '../../../../core/services/auth';
import { clienteGuard } from '../../../../core/guards/cliente-guard';
import { MoneyDecimal } from '../../../../shared/utils/money.util';
import { ContaService } from '../../services/conta';
import { DepositoService } from '../../services/deposito';
import { ExtratoService } from '../../services/extrato';
import { routes } from '../../../../app.routes';
import { Deposito } from './deposito';

describe('Deposito', () => {
  let fixture: ComponentFixture<Deposito>;
  let component: Deposito;
  let authService: { obterTipo: ReturnType<typeof vi.fn> };
  let extratoService: { obterExtrato: ReturnType<typeof vi.fn> };
  let depositoService: { depositar: ReturnType<typeof vi.fn> };
  let contaService: { obterResumo: ReturnType<typeof vi.fn> };

  const executeGuard = () =>
    TestBed.runInInjectionContext(() => clienteGuard({} as never, {} as never));

  beforeEach(async () => {
    authService = { obterTipo: vi.fn().mockReturnValue('CLIENTE') };
    extratoService = {
      obterExtrato: vi.fn().mockReturnValue(
        of({ numeroConta: '12345', saldoAbertura: '0', movimentacoes: [] }),
      ),
    };
    depositoService = {
      depositar: vi.fn().mockReturnValue(of({ mensagem: 'Depósito confirmado' })),
    };
    contaService = {
      obterResumo: vi.fn().mockReturnValue(of({ numeroConta: '12345', saldo: new MoneyDecimal('950') })),
    };

    await TestBed.configureTestingModule({
      imports: [Deposito],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: authService },
        { provide: ExtratoService, useValue: extratoService },
        { provide: DepositoService, useValue: depositoService },
        { provide: ContaService, useValue: contaService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Deposito);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renderiza a conta somente leitura e o campo obrigatório de valor', () => {
    const elemento = fixture.nativeElement as HTMLElement;
    const conta = elemento.querySelector<HTMLInputElement>('input[name="conta"]');

    expect(conta?.value).toBe('12345');
    expect(conta?.readOnly).toBe(true);
    expect(elemento.querySelector('input[name="valor"]')).toBeTruthy();
    expect(elemento.textContent).toContain('Depósito');
  });

  it('rejeita valor vazio', () => {
    component.continuar();

    expect(component.etapa()).toBe('dados');
    expect(component.erro()).toBe('Informe o valor do depósito.');
    expect(depositoService.depositar).not.toHaveBeenCalled();
  });

  it('rejeita valor zero', () => {
    component.valor = '0';
    component.continuar();

    expect(component.erro()).toBe('O valor deve ser maior que zero.');
    expect(depositoService.depositar).not.toHaveBeenCalled();
  });

  it('rejeita valor negativo', () => {
    component.valor = '-10';
    component.continuar();

    expect(component.etapa()).toBe('dados');
    expect(component.erro()).toContain('valor positivo');
    expect(depositoService.depositar).not.toHaveBeenCalled();
  });

  it('rejeita mais de duas casas decimais', () => {
    component.valor = '10,123';
    component.continuar();

    expect(component.etapa()).toBe('dados');
    expect(component.erro()).toContain('duas casas decimais');
  });

  it('normaliza vírgula e completa os centavos para confirmação', () => {
    component.valor = '150,5';
    component.continuar();

    expect(component.etapa()).toBe('confirmacao');
    expect(component.valorNormalizado).toBe('150.50');
  });

  it('impede o envio quando não consegue carregar a conta do cliente', () => {
    fixture.destroy();
    extratoService.obterExtrato.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0 })),
    );
    fixture = TestBed.createComponent(Deposito);
    component = fixture.componentInstance;
    fixture.detectChanges();

    component.valor = '10';
    component.continuar();

    expect(component.conta()).toBeNull();
    expect(component.carregandoConta()).toBe(false);
    expect(component.erroConta()).toContain('Não foi possível carregar a conta');
    expect(component.erro()).toContain('conta do cliente');
    expect(depositoService.depositar).not.toHaveBeenCalled();
  });

  it('pede confirmação antes de chamar o serviço', () => {
    component.valor = '150';
    component.continuar();

    expect(component.etapa()).toBe('confirmacao');
    expect(depositoService.depositar).not.toHaveBeenCalled();

    component.confirmar();

    expect(depositoService.depositar).toHaveBeenCalledWith('12345', '150.00');
  });

  it('mostra sucesso somente após resposta da API', () => {
    component.valor = '150';
    component.continuar();
    component.confirmar();
    fixture.detectChanges();

    expect(component.etapa()).toBe('sucesso');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Depósito realizado');
  });

  it('reconsulta o saldo depois do depósito', () => {
    component.valor = '150';
    component.continuar();
    component.confirmar();
    fixture.detectChanges();

    expect(contaService.obterResumo).toHaveBeenCalledOnce();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Saldo atual: R$ 950,00');
  });

  it('mostra erro HTTP sem indicar sucesso', () => {
    depositoService.depositar.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 422 })),
    );
    component.valor = '150';
    component.continuar();
    component.confirmar();

    expect(component.etapa()).toBe('confirmacao');
    expect(component.erro()).toContain('A API não concluiu o depósito');
  });

  it('informa quando o servidor está indisponível', () => {
    depositoService.depositar.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 0 })),
    );
    component.valor = '150';
    component.continuar();
    component.confirmar();

    expect(component.erro()).toContain('Não foi possível conectar ao servidor');
    expect(component.etapa()).toBe('confirmacao');
  });

  it('impede envios duplicados enquanto a requisição está pendente', () => {
    const respostaPendente = new Subject<unknown>();
    depositoService.depositar.mockReturnValue(respostaPendente);
    component.valor = '150';
    component.continuar();

    component.confirmar();
    component.confirmar();

    expect(depositoService.depositar).toHaveBeenCalledTimes(1);
    expect(component.enviando()).toBe(true);

    respostaPendente.next({});
    respostaPendente.complete();

    expect(component.etapa()).toBe('sucesso');
  });

  it('reutiliza clienteGuard na rota /deposito e bloqueia ADMIN', () => {
    const rotaDeposito = routes
      .flatMap((rota) => rota.children ?? [])
      .find((rota) => rota.path === 'deposito');
    authService.obterTipo.mockReturnValue('ADMIN');

    const resultado = executeGuard() as UrlTree;

    expect(rotaDeposito?.canActivate).toContain(clienteGuard);
    expect(TestBed.inject(Router).serializeUrl(resultado)).toBe('/login');  });
});
