import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import Decimal from 'decimal.js';
import { map, Observable, of, tap, throwError } from 'rxjs';

import { AuthService } from '../../../core/services/auth';
import { environment } from '../../../../environments/environment';
import { obterCpfDoToken } from '../../../shared/utils/jwt.util';
import { MoneyDecimal } from '../../../shared/utils/money.util';
import { ContaDto } from '../models/conta.model';

export interface ResumoConta {
  numeroConta: string;
  saldo: Decimal;
}

@Injectable({
  providedIn: 'root',
})
export class ContaService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);

  private readonly clientesUrl = `${environment.apiUrl}/clientes`;

  // O número da conta não muda: fica guardado por CPF para o extrato e as
  // operações não precisarem consultá-lo de novo. O saldo nunca é guardado.
  private contaConhecida: { cpf: string; numero: string } | null = null;

  /** R3: número da conta e saldo atual do cliente logado, lidos do lado query. */
  obterResumo(): Observable<ResumoConta> {
    const cpf = this.cpfDoUsuario();
    if (!cpf) {
      return throwError(() => new Error('Não foi possível identificar o cliente logado.'));
    }

    return this.http.get<ContaDto>(`${this.clientesUrl}/${cpf}/conta`).pipe(
      tap((conta) => (this.contaConhecida = { cpf, numero: conta.numero })),
      map((conta) => ({
        numeroConta: conta.numero,
        saldo: new MoneyDecimal(conta.saldo || '0'),
      })),
    );
  }

  obterNumeroConta(): Observable<string> {
    const cpf = this.cpfDoUsuario();
    if (cpf && this.contaConhecida?.cpf === cpf) {
      return of(this.contaConhecida.numero);
    }

    return this.obterResumo().pipe(map((resumo) => resumo.numeroConta));
  }

  private cpfDoUsuario(): string | null {
    return this.auth.obterUsuario()?.cpf || obterCpfDoToken(this.auth.obterToken());
  }
}
