import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AuthService } from '../../../core/services/auth';
import { hoje } from '../utils/linha-do-tempo';
import { ContaService } from './conta';
import { ExtratoService } from './extrato';

describe('ContaService', () => {
  it('calcula o saldo atual com o saldo de abertura e as movimentações de hoje', () => {
    const dia = hoje();
    const extratoService = {
      obterExtrato: vi.fn().mockReturnValue(
        of({
          numeroConta: '1291',
          saldoAbertura: '800.0000',
          movimentacoes: [
            { dataHora: dia.set({ hour: 10 }).toISO(), tipo: 'DEPOSITO', valor: '200.0000' },
            { dataHora: dia.set({ hour: 11 }).toISO(), tipo: 'SAQUE', valor: '50.0000' },
          ],
        }),
      ),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: ExtratoService, useValue: extratoService },
        { provide: AuthService, useValue: { obterUsuario: () => ({ cpf: '12912861012' }) } },
      ],
    });

    let resultado: { numeroConta: string; saldo: string } | undefined;
    TestBed.inject(ContaService)
      .obterResumo()
      .subscribe((c) => (resultado = { numeroConta: c.numeroConta, saldo: c.saldo.toFixed(2) }));

    expect(extratoService.obterExtrato).toHaveBeenCalledWith(dia, dia);
    expect(resultado).toEqual({ numeroConta: '1291', saldo: '950.00' });
  });
});
