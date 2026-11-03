import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../../core/services/auth';
import { CpfPipe } from '../../../../shared/pipes/cpf.pipe';
import { GerenteDto } from '../../models/gerente.model';
import { GerentesService } from '../../services/gerentes';

@Component({
  selector: 'app-gerentes',
  imports: [RouterLink, CpfPipe],
  templateUrl: './gerentes.html',
  styleUrl: './gerentes.scss',
})
export class Gerentes implements OnInit {
  private gerentesService = inject(GerentesService);
  private auth = inject(AuthService);
  private destroyRef = inject(DestroyRef);

  // R15: o gerente logado não pode remover a si mesmo
  readonly cpfLogado = this.auth.obterUsuario()?.cpf ?? null;

  readonly gerentes = signal<GerenteDto[]>([]);
  readonly carregando = signal(false);
  readonly erro = signal('');
  readonly mensagem = signal('');

  // Modal de confirmação da remoção
  readonly emRemocao = signal<GerenteDto | null>(null);
  readonly removendo = signal(false);
  readonly erroRemocao = signal('');

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set('');

    this.gerentesService
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => {
          this.gerentes.set(lista);
          this.carregando.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.erro.set(
            err.status === 0
              ? 'Não foi possível conectar ao servidor. Tente novamente.'
              : 'Não foi possível carregar os gerentes.',
          );
          this.carregando.set(false);
        },
      });
  }

  abrirRemocao(gerente: GerenteDto): void {
    this.erroRemocao.set('');
    this.emRemocao.set(gerente);
  }

  fecharRemocao(): void {
    if (this.removendo()) {
      return;
    }
    this.emRemocao.set(null);
  }

  confirmarRemocao(): void {
    const gerente = this.emRemocao();
    if (!gerente || this.removendo()) {
      return;
    }

    this.mensagem.set('');
    this.erroRemocao.set('');
    this.removendo.set(true);

    this.gerentesService
      .remover(gerente.cpf)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (mensagem) => {
          this.removendo.set(false);
          this.emRemocao.set(null);
          this.mensagem.set(mensagem);
          this.carregar();
        },
        error: (err: HttpErrorResponse | Error) => {
          this.removendo.set(false);
          this.erroRemocao.set(this.mensagemDeErro(err));
        },
      });
  }

  // 403: auto-remoção (Gateway); FALHA do job: ex. último gerente ativo (vem em job.erro)
  private mensagemDeErro(err: HttpErrorResponse | Error): string {
    if (!(err instanceof HttpErrorResponse)) {
      return err.message;
    }
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Tente novamente.';
    }
    if (err.status === 403) {
      return err.error?.message || 'Você não pode remover a si mesmo.';
    }
    return err.error?.message || err.error?.erro || 'Não foi possível remover o gerente.';
  }
}
