import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';

import { BrlCurrencyPipe } from '../../../../shared/pipes/brl-currency.pipe';
import { CpfPipe } from '../../../../shared/pipes/cpf.pipe';
import { mensagemDaApi } from '../../../../shared/utils/erro-api.util';
import { LinhaRelatorio } from '../../models/cliente.model';
import { ClientesService } from '../../services/clientes';

@Component({
  selector: 'app-relatorio',
  imports: [BrlCurrencyPipe, CpfPipe],
  templateUrl: './relatorio.html',
  styleUrl: './relatorio.scss',
})
export class Relatorio implements OnInit {
  private clientesService = inject(ClientesService);
  private destroyRef = inject(DestroyRef);

  readonly linhas = signal<LinhaRelatorio[]>([]);
  readonly gerando = signal(false);
  readonly gerado = signal(false);
  readonly erro = signal('');

  ngOnInit(): void {
    this.gerar();
  }

  gerar(): void {
    if (this.gerando()) {
      return;
    }

    this.gerando.set(true);
    this.erro.set('');

    this.clientesService
      .gerarRelatorio()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (linhas) => {
          this.linhas.set(linhas);
          this.gerado.set(true);
          this.gerando.set(false);
        },
        error: (err: HttpErrorResponse | Error) => {
          this.erro.set(this.mensagemDeErro(err));
          this.gerando.set(false);
        },
      });
  }

  private mensagemDeErro(err: HttpErrorResponse | Error): string {
    if (!(err instanceof HttpErrorResponse)) {
      return err.message;
    }
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Tente novamente.';
    }
    return mensagemDaApi(err, 'Não foi possível gerar o relatório.');
  }
}
