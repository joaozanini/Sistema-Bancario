import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { AuthService } from '../../core/services/auth';
import { ContaService } from '../../features/cliente/services/conta';

@Component({
  selector: 'app-header',
  imports: [],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class HeaderComponent {
  private readonly auth = inject(AuthService);
  private readonly contaService = inject(ContaService);
  private readonly destroyRef = inject(DestroyRef);

  readonly usuario = this.auth.obterUsuario();
  private readonly tipo = this.auth.obterTipo();

  readonly ehGerente = this.tipo === 'GERENTE';

  readonly iniciais = obterIniciais(this.usuario?.nome);

  readonly numeroConta = signal<string | null>(null);

  constructor() {
    if (this.tipo === 'CLIENTE') {
      this.contaService
        .obterResumo()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (conta) => this.numeroConta.set(conta.numeroConta),
          error: () => this.numeroConta.set(null),
        });
    }
  }
}

function obterIniciais(nome: string | undefined): string {
  const partes = (nome ?? '').trim().split(/\s+/).filter(Boolean);
  if (partes.length === 0) {
    return '?';
  }
  const primeira = partes[0][0];
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : '';
  return (primeira + ultima).toUpperCase();
}
