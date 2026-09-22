import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { DateTime } from 'luxon';
import { Subscription } from 'rxjs';

import { AuthService } from '../../../../core/services/auth';
import { BrlCurrencyPipe } from '../../../../shared/pipes/brl-currency.pipe';
import { BrDateTimePipe } from '../../../../shared/pipes/br-datetime.pipe';
import { BrDatePipe } from '../../../../shared/pipes/br-date.pipe';
import { obterCpfDoToken } from '../../../../shared/utils/jwt.util';
import { LinhaDoTempo } from '../../models/extrato.model';
import { ExtratoService } from '../../services/extrato';
import {
  hoje,
  lerDataInput,
  paraDataInput,
  periodoPadrao,
  montarLinhaDoTempo,
  validarPeriodo,
} from '../../utils/linha-do-tempo';

@Component({
  selector: 'app-extrato',
  imports: [FormsModule, BrlCurrencyPipe, BrDateTimePipe, BrDatePipe],
  templateUrl: './extrato.html',
  styleUrl: './extrato.scss',
})
export class Extrato implements OnInit {
  private extratoService = inject(ExtratoService);
  private auth = inject(AuthService);
  private destroyRef = inject(DestroyRef);

  dataInicio = '';
  dataFim = '';
  readonly hojeInput = paraDataInput(hoje());

  readonly carregando = signal(false);
  readonly erro = signal('');
  readonly linhaDoTempo = signal<LinhaDoTempo | null>(null);
  readonly fimConsultado = signal<DateTime | null>(null);

  private consulta?: Subscription;

  ngOnInit(): void {
    const { inicio, fim } = periodoPadrao();
    this.dataInicio = paraDataInput(inicio);
    this.dataFim = paraDataInput(fim);
    this.consultar();
  }

  consultar(): void {
    const inicio = lerDataInput(this.dataInicio);
    const fim = lerDataInput(this.dataFim);
    const erroPeriodo = validarPeriodo(inicio, fim);

    if (erroPeriodo || !inicio || !fim) {
      this.erro.set(erroPeriodo ?? 'Período inválido.');
      return;
    }

    this.erro.set('');
    this.carregando.set(true);
    this.consulta?.unsubscribe();

    const cpfUsuario = obterCpfDoToken(this.auth.obterToken());

    this.consulta = this.extratoService
      .obterExtrato(inicio, fim)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (extrato) => {
          this.linhaDoTempo.set(montarLinhaDoTempo(extrato, inicio, fim, cpfUsuario));
          this.fimConsultado.set(fim);
          this.carregando.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.linhaDoTempo.set(null);
          this.erro.set(
            err.status === 0
              ? 'Não foi possível conectar ao servidor. Tente novamente.'
              : 'Não foi possível carregar o extrato. Tente novamente.',
          );
          this.carregando.set(false);
        },
      });
  }

  ehHoje(data: DateTime | null): boolean {
    return !!data && data.hasSame(hoje(), 'day');
  }
}