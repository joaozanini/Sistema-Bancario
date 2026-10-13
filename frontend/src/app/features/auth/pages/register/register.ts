import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { MoneyDecimal } from '../../../../shared/utils/money.util';
import { AutocadastroService } from '../../services/autocadastro';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  private autocadastroService = inject(AutocadastroService);
  private destroyRef = inject(DestroyRef);

  nome = '';
  cpf = '';
  telefone = '';
  email = '';
  salario: number | null = null;
  logradouro = '';
  numero = '';
  complemento = '';
  cep = '';
  cidade = '';
  estado = '';
  solicitacaoEnviada = false;
  enviando = false;
  erro = '';

  readonly estados = [
    'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS',
    'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC',
    'SP', 'SE', 'TO',
  ];

  enviarSolicitacao(form: NgForm) {
    this.solicitacaoEnviada = false;
    this.erro = '';

    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }

    this.enviando = true;

    this.autocadastroService
      .solicitar({
        nome: this.nome.trim(),
        cpf: this.cpf.replace(/\D/g, ''),
        email: this.email.trim(),
        telefone: this.telefone.trim(),
        salario: new MoneyDecimal(this.salario ?? 0).toFixed(2),
        logradouro: this.logradouro.trim(),
        numero: this.numero.trim(),
        complemento: this.complemento.trim(),
        cep: this.cep,
        cidade: this.cidade.trim(),
        uf: this.estado,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.enviando = false;
          this.solicitacaoEnviada = true;
          form.resetForm({ estado: '' });
        },
        error: (err: HttpErrorResponse) => {
          this.enviando = false;
          this.erro = this.mensagemDeErro(err);
        },
      });
  }

  // o MS Cliente devolve 400 { erro } para CPF ou e-mail já usados em outra solicitação
  private mensagemDeErro(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Tente novamente.';
    }
    return err.error?.erro || err.error?.message || 'Não foi possível enviar a solicitação.';
  }
}
