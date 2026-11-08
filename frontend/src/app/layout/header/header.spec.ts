import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AuthService } from '../../core/services/auth';
import { ContaService } from '../../features/cliente/services/conta';
import { MoneyDecimal } from '../../shared/utils/money.util';
import { HeaderComponent } from './header';

describe('Header', () => {
  let fixture: ComponentFixture<HeaderComponent>;
  let contaService: { obterResumo: ReturnType<typeof vi.fn> };

  async function criar(tipo: 'CLIENTE' | 'GERENTE') {
    contaService = {
      obterResumo: vi.fn().mockReturnValue(of({ numeroConta: '1291', saldo: new MoneyDecimal('800') })),
    };

    await TestBed.configureTestingModule({
      imports: [HeaderComponent],
      providers: [
        {
          provide: AuthService,
          useValue: {
            obterUsuario: () => ({ cpf: '12912861012', nome: 'Catharyna Silva', email: 'cli1@bantads.com.br' }),
            obterTipo: () => tipo,
          },
        },
        { provide: ContaService, useValue: contaService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HeaderComponent);
    fixture.detectChanges();
  }

  it('mostra as iniciais e o número da conta do cliente', async () => {
    await criar('CLIENTE');

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('CS');
    expect(texto).toContain('Conta 1291');
  });

  it('mostra o perfil do gerente sem consultar conta', async () => {
    await criar('GERENTE');

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('(Gerente)');
    expect(texto).toContain('cli1@bantads.com.br');
    expect(contaService.obterResumo).not.toHaveBeenCalled();
  });
});
