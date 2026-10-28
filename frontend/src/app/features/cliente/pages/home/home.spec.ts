import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { MoneyDecimal } from '../../../../shared/utils/money.util';
import { ContaService } from '../../services/conta';
import { Home } from './home';

describe('Home do cliente', () => {
  let fixture: ComponentFixture<Home>;
  let contaService: { obterResumo: ReturnType<typeof vi.fn> };

  async function criar() {
    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideRouter([]), { provide: ContaService, useValue: contaService }],
    }).compileComponents();

    fixture = TestBed.createComponent(Home);
    fixture.detectChanges();
  }

  it('mostra o número da conta e o saldo vindos do back', async () => {
    contaService = {
      obterResumo: vi.fn().mockReturnValue(of({ numeroConta: '1291', saldo: new MoneyDecimal('950') })),
    };
    await criar();

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('1291');
    expect(texto).toContain('R$ 950,00');
    expect(texto).not.toContain('Demonstração');
  });

  it('mostra erro e permite tentar de novo', async () => {
    contaService = {
      obterResumo: vi.fn().mockReturnValue(throwError(() => new HttpErrorResponse({ status: 502 }))),
    };
    await criar();

    expect(fixture.nativeElement.textContent).toContain('Não foi possível carregar sua conta.');
  });
});
