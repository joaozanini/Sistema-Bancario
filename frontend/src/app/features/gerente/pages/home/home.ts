import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { BrlCurrencyPipe } from '../../../../shared/pipes/brl-currency.pipe';
import { BrDateTimePipe } from '../../../../shared/pipes/br-datetime.pipe';
import { CpfPipe } from '../../../../shared/pipes/cpf.pipe';
import { FiltroStatus, Solicitacao } from '../../models/solicitacao.model';
import { SolicitacoesService } from '../../services/solicitacoes';
import { FILTROS, filtrarPorStatus, ROTULO_STATUS } from '../../utils/solicitacoes';

@Component({
  selector: 'app-home',
  imports: [FormsModule, BrlCurrencyPipe, BrDateTimePipe, CpfPipe],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit {
  private solicitacoesService = inject(SolicitacoesService);
  private destroyRef = inject(DestroyRef);

  readonly filtros = FILTROS;
  readonly rotuloStatus = ROTULO_STATUS;

  readonly solicitacoes = signal<Solicitacao[]>([]);
  readonly filtro = signal<FiltroStatus>('TODAS');
  readonly carregando = signal(false);
  readonly erro = signal('');
  readonly mensagem = signal('');
  readonly processandoId = signal<string | null>(null);

  // Modal de recusa (R10 exige o motivo)
  readonly emRecusa = signal<Solicitacao | null>(null);
  readonly erroMotivo = signal('');
  motivo = '';

  readonly visiveis = computed(() => filtrarPorStatus(this.solicitacoes(), this.filtro()));

  readonly totalPorFiltro = computed(() => {
    const lista = this.solicitacoes();
    return Object.fromEntries(
      this.filtros.map((f) => [f.valor, filtrarPorStatus(lista, f.valor).length]),
    ) as Record<FiltroStatus, number>;
  });

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set('');

    this.solicitacoesService
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => {
          this.solicitacoes.set(lista);
          this.carregando.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.erro.set(this.mensagemDeErro(err, 'Não foi possível carregar as solicitações.'));
          this.carregando.set(false);
        },
      });
  }

  selecionarFiltro(filtro: FiltroStatus): void {
    this.filtro.set(filtro);
  }

  aprovar(solicitacao: Solicitacao): void {
    if (this.processandoId()) {
      return;
    }

    this.mensagem.set('');
    this.erro.set('');
    this.processandoId.set(solicitacao.id);

    this.solicitacoesService
      .aprovar(solicitacao.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.processandoId.set(null);
          this.mensagem.set(`Aprovação de ${solicitacao.nome} enviada.`);
          this.carregar();
        },
        error: (err: HttpErrorResponse) => {
          this.processandoId.set(null);
          this.erro.set(this.mensagemDeErro(err, 'Não foi possível aprovar a solicitação.'));
        },
      });
  }

  abrirRecusa(solicitacao: Solicitacao): void {
    this.motivo = '';
    this.erroMotivo.set('');
    this.emRecusa.set(solicitacao);
  }

  fecharRecusa(): void {
    if (this.processandoId()) {
      return;
    }
    this.emRecusa.set(null);
  }

  confirmarRecusa(): void {
    const solicitacao = this.emRecusa();
    const motivo = this.motivo.trim();

    if (!solicitacao) {
      return;
    }
    if (!motivo) {
      this.erroMotivo.set('Informe o motivo da recusa.');
      return;
    }

    this.mensagem.set('');
    this.erro.set('');
    this.erroMotivo.set('');
    this.processandoId.set(solicitacao.id);

    this.solicitacoesService
      .recusar(solicitacao.id, motivo)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.processandoId.set(null);
          this.emRecusa.set(null);
          this.mensagem.set(`Solicitação de ${solicitacao.nome} recusada.`);
          this.carregar();
        },
        error: (err: HttpErrorResponse) => {
          this.processandoId.set(null);
          this.erroMotivo.set(this.mensagemDeErro(err, 'Não foi possível recusar a solicitação.'));
        },
      });
  }

  private mensagemDeErro(err: HttpErrorResponse, padrao: string): string {
    return err.status === 0 ? 'Não foi possível conectar ao servidor. Tente novamente.' : padrao;
  }
}
