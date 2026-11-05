import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';

import { BrlCurrencyPipe } from '../../../../shared/pipes/brl-currency.pipe';
import { CpfPipe } from '../../../../shared/pipes/cpf.pipe';
import { Cliente } from '../../models/cliente.model';
import { ClientesService } from '../../services/clientes';

export function filtrarClientes(clientes: Cliente[], busca: string): Cliente[] {
  const termo = normalizar(busca);
  if (!termo) {
    return clientes;
  }

  const digitos = busca.replace(/\D/g, '');
  return clientes.filter(
    (c) => normalizar(c.nome).includes(termo) || (digitos !== '' && c.cpf.includes(digitos)),
  );
}

function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

@Component({
  selector: 'app-clientes',
  imports: [FormsModule, BrlCurrencyPipe, CpfPipe],
  templateUrl: './clientes.html',
  styleUrl: './clientes.scss',
})
export class Clientes implements OnInit {
  private clientesService = inject(ClientesService);
  private destroyRef = inject(DestroyRef);

  readonly clientes = signal<Cliente[]>([]);
  readonly busca = signal('');
  readonly carregando = signal(false);
  readonly erro = signal('');

  readonly visiveis = computed(() => filtrarClientes(this.clientes(), this.busca()));

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set('');

    this.clientesService
      .listar()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => {
          this.clientes.set(lista);
          this.carregando.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.erro.set(
            err.status === 0
              ? 'Não foi possível conectar ao servidor. Tente novamente.'
              : 'Não foi possível carregar os clientes.',
          );
          this.carregando.set(false);
        },
      });
  }
}
