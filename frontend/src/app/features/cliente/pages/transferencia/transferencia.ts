import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { BrlCurrencyPipe } from '../../../../shared/pipes/brl-currency.pipe';
import { periodoPadrao } from '../../utils/linha-do-tempo';
import { ExtratoService } from '../../services/extrato';
import { TransferenciaService } from '../../services/transferencia';

type EtapaTransferencia = 'dados' | 'confirmacao' | 'sucesso';

@Component({
  selector: 'app-transferencia',
  imports: [FormsModule, RouterLink, BrlCurrencyPipe],
  templateUrl: './transferencia.html',
  styleUrl: './transferencia.scss',
})
export class Transferencia implements OnInit {
  private readonly extratoService = inject(ExtratoService);
  private readonly transferenciaService = inject(TransferenciaService);
  private readonly destroyRef = inject(DestroyRef);

  readonly contaOrigem = signal<string | null>(null);
  readonly carregandoConta = signal(true);
  readonly erroConta = signal('');
  readonly etapa = signal<EtapaTransferencia>('dados');
  readonly enviando = signal(false);
  readonly erro = signal('');

  contaDestino = '';
  valor = '';

  ngOnInit(): void {
    const { inicio, fim } = periodoPadrao();

    this.extratoService
      .obterExtrato(inicio, fim)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (extrato) => {
          const numeroConta = extrato.numeroConta?.trim();
          this.contaOrigem.set(numeroConta || null);
          this.erroConta.set(
            numeroConta ? '' : 'A API não retornou o número da conta de origem.',
          );
          this.carregandoConta.set(false);
        },
        error: () => {
          this.contaOrigem.set(null);
          this.erroConta.set(
            'Não foi possível carregar a conta de origem. O envio ficará indisponível até que a API retorne essa informação.',
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

    const origem = this.contaOrigem();
    if (!origem) {
      this.erro.set('A conta de origem ainda não está disponível.');
      return;
    }

    const destino = this.contaDestino.trim();
    if (!destino) {
      this.erro.set('Informe a conta de destino.');
      return;
    }

    if (this.normalizarConta(destino) === this.normalizarConta(origem)) {
      this.erro.set('A conta de destino deve ser diferente da conta de origem.');
      return;
    }

    const valorInformado = this.valor.trim();
    if (!valorInformado) {
      this.erro.set('Informe o valor da transferência.');
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

    this.contaDestino = destino;
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

    const origem = this.contaOrigem();
    const valor = this.normalizarValor(this.valor);
    const destino = this.contaDestino.trim();

    if (!origem || !destino || !valor) {
      this.voltarParaEdicao();
      this.erro.set('Revise os dados da transferência antes de continuar.');
      return;
    }

    this.erro.set('');
    this.enviando.set(true);

    this.transferenciaService
      .transferir(origem, destino, valor)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.enviando.set(false);
          this.etapa.set('sucesso');
        },
        error: (erro: unknown) => {
          this.enviando.set(false);
          this.erro.set(this.mensagemErroApi(erro));
        },
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

  private normalizarConta(conta: string): string {
    const digitos = conta.replace(/\D/g, '');
    return digitos || conta.trim().toLocaleLowerCase();
  }

  private mensagemErroApi(erro: unknown): string {
    if (!(erro instanceof HttpErrorResponse)) {
      return 'Não foi possível concluir a transferência. Tente novamente.';
    }

    if (erro.status === 0) {
      return 'Não foi possível conectar ao servidor. Tente novamente quando a API estiver disponível.';
    }

    const corpo = erro.error as { message?: unknown; error?: unknown } | string | null;
    let mensagem = '';
    if (typeof corpo === 'string') {
      mensagem = corpo;
    } else if (corpo && typeof corpo.message === 'string') {
      mensagem = corpo.message;
    } else if (corpo && typeof corpo.error === 'string') {
      mensagem = corpo.error;
    }
    const textoNormalizado = mensagem
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLocaleLowerCase();

    if (textoNormalizado.includes('saldo insuficiente') || textoNormalizado.includes('insufficient funds')) {
      return 'Saldo insuficiente para realizar a transferência.';
    }

    if (
      erro.status === 404 ||
      /conta.*(inexistente|nao encontrada|nao existe)/.test(textoNormalizado) ||
      textoNormalizado.includes('account not found')
    ) {
      return 'Não foi possível localizar uma das contas informadas. Confira a conta de destino.';
    }

    return 'A API não concluiu a transferência. Confira os dados e tente novamente.';
  }
}
