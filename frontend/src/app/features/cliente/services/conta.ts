import { inject, Injectable } from '@angular/core';
import Decimal from 'decimal.js';
import { map, Observable } from 'rxjs';

import { AuthService } from '../../../core/services/auth';
import { hoje, montarLinhaDoTempo } from '../utils/linha-do-tempo';
import { ExtratoService } from './extrato';

export interface ResumoConta {
  numeroConta: string;
  saldo: Decimal;
}

@Injectable({
  providedIn: 'root',
})
export class ContaService {
  private extratoService = inject(ExtratoService);
  private auth = inject(AuthService);

  obterResumo(): Observable<ResumoConta> {
    const dia = hoje();
    const cpf = this.auth.obterUsuario()?.cpf ?? null;

    return this.extratoService.obterExtrato(dia, dia).pipe(
      map((extrato) => ({
        numeroConta: extrato.numeroConta,
        saldo: montarLinhaDoTempo(extrato, dia, dia, cpf).saldoFinal,
      })),
    );
  }
}
