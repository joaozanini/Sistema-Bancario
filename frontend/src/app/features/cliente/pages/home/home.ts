import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../../core/services/auth';
import { Icone } from '../../../../shared/components/icone/icone';
import { NomeIcone } from '../../../../shared/components/icone/icones';
import { BrlCurrencyPipe } from '../../../../shared/pipes/brl-currency.pipe';
import { ContaService, ResumoConta } from '../../services/conta';

interface Atalho {
  rotulo: string;
  descricao: string;
  link: string;
  icone: NomeIcone;
}

@Component({
  selector: 'app-home',
  imports: [RouterLink, BrlCurrencyPipe, Icone],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit {
  private readonly contaService = inject(ContaService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);

  readonly primeiroNome = (this.auth.obterUsuario()?.nome ?? '').trim().split(/\s+/)[0];

  readonly atalhos: Atalho[] = [
    {
      rotulo: 'Depósito',
      descricao: 'Adicione dinheiro à sua conta.',
      link: '/deposito',
      icone: 'deposito',
    },
    {
      rotulo: 'Saque',
      descricao: 'Retire dinheiro da sua conta, dentro do saldo disponível.',
      link: '/saque',
      icone: 'saque',
    },
    {
      rotulo: 'Transferência',
      descricao: 'Envie valores para outra conta BANTADS.',
      link: '/transferencia',
      icone: 'transferencia',
    },
    {
      rotulo: 'Extrato',
      descricao: 'Consulte suas movimentações e o saldo de cada dia.',
      link: '/extrato',
      icone: 'extrato',
    },
  ];

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
