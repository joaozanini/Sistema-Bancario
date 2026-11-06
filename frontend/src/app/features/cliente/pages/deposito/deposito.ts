import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import Decimal from 'decimal.js';

import { SaldoDisponivel } from '../../../../shared/components/saldo-disponivel/saldo-disponivel';
import { BrlCurrencyPipe } from '../../../../shared/pipes/brl-currency.pipe';
import { ContaService } from '../../services/conta';
import { DepositoService } from '../../services/deposito';

type EtapaDeposito = 'dados' | 'confirmacao' | 'sucesso';

@Component({
  selector: 'app-deposito',
  imports: [FormsModule, RouterLink, BrlCurrencyPipe, SaldoDisponivel],
  templateUrl: './deposito.html',
  styleUrl: './deposito.scss',
})
export class Deposito implements OnInit {
  private readonly depositoService = inject(DepositoService);
  private readonly contaService = inject(ContaService);
  private readonly destroyRef = inject(DestroyRef);

  readonly saldo = signal<Decimal | null>(null);
  readonly saldoAtual = signal<Decimal | null>(null);
  readonly erroSaldo = signal(false);

  readonly conta = signal<string | null>(null);
  readonly carregandoConta = signal(true);
  readonly erroConta = signal('');
  readonly etapa = signal<EtapaDeposito>('dados');
  readonly enviando = signal(false);
  readonly erro = signal('');

  valor = '';

  ngOnInit(): void {
    this.contaService
      .obterResumo()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resumo) => {
          const numeroConta = resumo.numeroConta?.trim();
          this.saldo.set(resumo.saldo);
          this.conta.set(numeroConta || null);
          this.erroConta.set(
            numeroConta ? '' : 'A API não retornou o número da conta do cliente.',
          );
          this.carregandoConta.set(false);
        },
        error: () => {
          this.conta.set(null);
          this.erroConta.set(
            'Não foi possível carregar a conta do cliente. O depósito ficará indisponível até que a API retorne essa informação.',
          );
          this.carregandoConta.set(false);
        },
      });
  }

  get valorNormalizado(): string {
    return this.normalizarValor(this.valor) ?? '0.00';
  }

  continuar(): void {
    this.erro.set('');

    if (!this.conta()) {
      this.erro.set('A conta do cliente ainda não está disponível.');
      return;
    }

    const valorInformado = this.valor.trim();
    if (!valorInformado) {
      this.erro.set('Informe o valor do depósito.');
      return;
    }

    if (!/^\d+(?:[.,]\d{1,2})?$/.test(valorInformado)) {
      this.erro.set('Informe um valor positivo com até duas casas decimais.');
      return;
    }

    if (!this.normalizarValor(valorInformado)) {
      this.erro.set('O valor deve ser maior que zero.');
      return;
    }

    this.etapa.set('confirmacao');
  }

  voltarParaEdicao(): void {
    if (this.enviando()) {
      return;
    }

    this.erro.set('');
    this.etapa.set('dados');
  }

  limparErro(): void {
    this.erro.set('');
  }

  confirmar(): void {
    if (this.etapa() !== 'confirmacao' || this.enviando()) {
      return;
    }

    const numeroConta = this.conta();
    const valor = this.normalizarValor(this.valor);
    if (!numeroConta || !valor) {
      this.voltarParaEdicao();
      this.erro.set('Revise os dados do depósito antes de continuar.');
      return;
    }

    this.erro.set('');
    this.enviando.set(true);

    this.depositoService
      .depositar(numeroConta, valor)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.enviando.set(false);
          this.etapa.set('sucesso');
          this.reconsultarSaldo();
        },
        error: (erro: unknown) => {
          this.enviando.set(false);
          this.erro.set(this.mensagemErroApi(erro));
        },
      });
  }

  private reconsultarSaldo(): void {
    this.saldoAtual.set(null);
    this.erroSaldo.set(false);

    this.contaService
      .obterResumo()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resumo) => this.saldoAtual.set(resumo.saldo),
        error: () => this.erroSaldo.set(true),
      });
  }

  private normalizarValor(valor: string): string | null {
    const valorInformado = valor.trim();
    if (!/^\d+(?:[.,]\d{1,2})?$/.test(valorInformado)) {
      return null;
    }

    const [inteiro, centavos = ''] = valorInformado.replace(',', '.').split('.');
    const reais = inteiro.replace(/^0+(?=\d)/, '');
    const parteDecimal = centavos.padEnd(2, '0');

    if (/^0+$/.test(reais) && /^0+$/.test(parteDecimal)) {
      return null;
    }

    return `${reais}.${parteDecimal}`;
  }

  private mensagemErroApi(erro: unknown): string {
    if (!(erro instanceof HttpErrorResponse)) {
      return 'Não foi possível concluir o depósito. Tente novamente.';
    }

    if (erro.status === 0) {
      return 'Não foi possível conectar ao servidor. Tente novamente quando a API estiver disponível.';
    }

    return 'A API não concluiu o depósito. Confira os dados e tente novamente.';
  }
}
