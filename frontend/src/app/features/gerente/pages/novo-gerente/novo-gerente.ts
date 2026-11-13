import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { MascaraDirective } from '../../../../shared/directives/mascara.directive';
import { mensagemDaApi } from '../../../../shared/utils/erro-api.util';
import { GerentesService } from '../../services/gerentes';

@Component({
  selector: 'app-novo-gerente',
  imports: [FormsModule, RouterLink, MascaraDirective],
  templateUrl: './novo-gerente.html',
  styleUrl: './novo-gerente.scss',
})
export class NovoGerente {
  private gerentesService = inject(GerentesService);
  private destroyRef = inject(DestroyRef);

  readonly rotaListagem = '/gerente/gerentes';

  readonly salvando = signal(false);
  readonly erro = signal('');
  readonly sucesso = signal('');

  nome = '';
  cpf = '';
  email = '';
  telefone = '';
  senha = '';

  salvar(form: NgForm): void {
    this.erro.set('');

    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }

    this.salvando.set(true);

    this.gerentesService
      .inserir({
        nome: this.nome.trim(),
        cpf: this.cpf.replace(/\D/g, ''),
        email: this.email.trim(),
        telefone: this.telefone.trim(),
        senha: this.senha,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (mensagem) => {
          this.salvando.set(false);
          this.sucesso.set(mensagem);
        },
        error: (err: HttpErrorResponse | Error) => {
          this.salvando.set(false);
          this.erro.set(this.mensagemDeErro(err));
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
    return mensagemDaApi(err, 'Não foi possível inserir o gerente.');
  }
}
