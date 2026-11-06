import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';

import { routes } from '../../../../app.routes';
import { clienteGuard } from '../../../../core/guards/cliente-guard';
import { MoneyDecimal } from '../../../../shared/utils/money.util';
import { ContaService } from '../../services/conta';
import { SaqueService } from '../../services/saque';
import { Saque } from './saque';

describe('Saque', () => {
  let fixture: ComponentFixture<Saque>;
  let component: Saque;
  let contaService: { obterResumo: ReturnType<typeof vi.fn> };
  let saqueService: { sacar: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    contaService = {
      obterResumo: vi
        .fn()
        .mockReturnValueOnce(of({ numeroConta: '1291', saldo: new MoneyDecimal('800') }))
        .mockReturnValue(of({ numeroConta: '1291', saldo: new MoneyDecimal('650') })),
    };
    saqueService = { sacar: vi.fn().mockReturnValue(of({})) };

    await TestBed.configureTestingModule({
      imports: [Saque],
      providers: [
        provideRouter([]),
        { provide: ContaService, useValue: contaService },
        { provide: SaqueService, useValue: saqueService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Saque);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('mostra a conta somente leitura e o saldo disponível', () => {
    const elemento = fixture.nativeElement as HTMLElement;
    const conta = elemento.querySelector<HTMLInputElement>('input[name="conta"]');

    expect(conta?.value).toBe('1291');
    expect(conta?.readOnly).toBe(true);
    expect(elemento.querySelector('app-saldo-disponivel')?.textContent).toContain('R$ 800,00');
  });

  it('rejeita valor vazio, zero e com mais de duas casas', () => {
    component.continuar();
    expect(component.erro()).toBe('Informe o valor do saque.');

    component.valor = '0';
    component.continuar();
    expect(component.erro()).toBe('O valor deve ser maior que zero.');

    component.valor = '10,123';
    component.continuar();
    expect(component.erro()).toBe('Informe um valor positivo com até duas casas decimais.');

    expect(component.etapa()).toBe('dados');
  });

  it('pede confirmação antes de sacar e envia o valor normalizado', () => {
    component.valor = '150,5';
    component.continuar();

    expect(component.etapa()).toBe('confirmacao');
    expect(saqueService.sacar).not.toHaveBeenCalled();

    component.confirmar();

    expect(saqueService.sacar).toHaveBeenCalledWith('1291', '150.50');
  });

  it('depois do sucesso reconsulta o saldo', () => {
    component.valor = '150';
    component.continuar();
    component.confirmar();
    fixture.detectChanges();

    expect(component.etapa()).toBe('sucesso');
    expect(contaService.obterResumo).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('Saldo atual: R$ 650,00');
  });

  it('mostra saldo insuficiente no 422', () => {
    saqueService.sacar.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 422, error: { erro: 'Saldo insuficiente' } })),
    );
    component.valor = '5000';
    component.continuar();
    component.confirmar();

    expect(component.etapa()).toBe('confirmacao');
    expect(component.erro()).toBe('Saldo insuficiente para realizar o saque.');
  });

  it('impede envios duplicados enquanto a requisição está pendente', () => {
    const pendente = new Subject<unknown>();
    saqueService.sacar.mockReturnValue(pendente);
    component.valor = '150';
    component.continuar();

    component.confirmar();
    component.confirmar();

    expect(saqueService.sacar).toHaveBeenCalledTimes(1);
    pendente.next({});
    pendente.complete();
    expect(component.etapa()).toBe('sucesso');
  });

  it('a rota /saque usa o clienteGuard', () => {
    const rota = routes.flatMap((r) => r.children ?? []).find((r) => r.path === 'saque');

    expect(rota?.canActivate).toContain(clienteGuard);
  });
});
