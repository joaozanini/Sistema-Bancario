import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';

import { BrlCurrencyPipe } from '../../../../shared/pipes/brl-currency.pipe';
import { ContaService, ResumoConta } from '../../services/conta';

@Component({
  selector: 'app-home',
  imports: [RouterLink, BrlCurrencyPipe],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit {
  private readonly contaService = inject(ContaService);
  private readonly destroyRef = inject(DestroyRef);

  readonly conta = signal<ResumoConta | null>(null);
  readonly carregando = signal(false);
  readonly erro = signal('');
  readonly mostrarSaldo = signal(true);

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set('');

    this.contaService
      .obterResumo()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (conta) => {
          this.conta.set(conta);
          this.carregando.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.erro.set(
            err.status === 0
              ? 'Não foi possível conectar ao servidor. Tente novamente.'
              : 'Não foi possível carregar sua conta.',
          );
          this.carregando.set(false);
        },
      });
  }

  alternarSaldo(): void {
    this.mostrarSaldo.update((visivel) => !visivel);
  }
}
