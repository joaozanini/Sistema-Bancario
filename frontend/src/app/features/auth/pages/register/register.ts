import { Component } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
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

  readonly estados = [
    'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS',
    'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC',
    'SP', 'SE', 'TO',
  ];

  enviarSolicitacao(form: NgForm) {
    this.solicitacaoEnviada = false;

    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }

    this.solicitacaoEnviada = true;
  }
}
