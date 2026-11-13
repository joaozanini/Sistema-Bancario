import { ChangeDetectorRef, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule, NgForm } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { CpfValidoDirective } from '../../../../shared/directives/cpf-valido.directive';
import { MascaraDirective } from '../../../../shared/directives/mascara.directive';
import { Logo } from '../../../../shared/components/logo/logo';
import { MoneyDecimal } from '../../../../shared/utils/money.util';
import { AutocadastroService } from '../../services/autocadastro';
import { CepNaoEncontradoError, CepService } from '../../services/cep';

export const MENSAGEM_CADASTRO_ENVIADO =
  'Solicitação de cadastro enviada! Você receberá sua senha por e-mail quando um gerente aprovar.';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink, Logo, MascaraDirective, CpfValidoDirective],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  private autocadastroService = inject(AutocadastroService);
  private cepService = inject(CepService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  private destroyRef = inject(DestroyRef);

  nome = '';
  cpf = '';
  telefone = '';
  email = '';
  salario = '';
  logradouro = '';
  numero = '';
  complemento = '';
  cep = '';
  cidade = '';
  estado = '';

  readonly enviando = signal(false);
  readonly erro = signal('');
  readonly buscandoCep = signal(false);
  readonly erroCep = signal('');

  private ultimoCepBuscado = '';

  readonly estados = [
    'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO', 'MA', 'MT', 'MS',
    'MG', 'PA', 'PB', 'PR', 'PE', 'PI', 'RJ', 'RN', 'RS', 'RO', 'RR', 'SC',
    'SP', 'SE', 'TO',
  ];

  aoAlterarCep(valor: string): void {
    const digitos = (valor ?? '').replace(/\D/g, '');
    this.erroCep.set('');

    if (digitos.length !== 8 || digitos === this.ultimoCepBuscado) {
      return;
    }

    this.ultimoCepBuscado = digitos;
    this.buscandoCep.set(true);

    this.cepService
      .buscar(digitos)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (endereco) => {
          this.logradouro = endereco.logradouro || this.logradouro;
          this.complemento = this.complemento || endereco.complemento;
          this.cidade = endereco.cidade || this.cidade;
          this.estado = this.estados.includes(endereco.uf) ? endereco.uf : this.estado;
          this.buscandoCep.set(false);
          this.cdr.markForCheck();
        },
        error: (err: unknown) => {
          this.buscandoCep.set(false);
          this.erroCep.set(
            err instanceof CepNaoEncontradoError
              ? 'CEP não encontrado. Confira o número ou preencha o endereço manualmente.'
              : 'Não foi possível buscar o CEP. Preencha o endereço manualmente.',
          );
        },
      });
  }

  enviarSolicitacao(form: NgForm) {
    this.erro.set('');

    if (form.invalid) {
      form.control.markAllAsTouched();
      return;
    }

    this.enviando.set(true);

    this.autocadastroService
      .solicitar({
        nome: this.nome.trim(),
        cpf: this.cpf.replace(/\D/g, ''),
        email: this.email.trim(),
        telefone: this.telefone.trim(),
        salario: new MoneyDecimal(this.salario || 0).toFixed(2),
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
          this.enviando.set(false);
          this.router.navigateByUrl('/login', { state: { mensagem: MENSAGEM_CADASTRO_ENVIADO } });
        },
        error: (err: HttpErrorResponse) => {
          this.enviando.set(false);
          this.erro.set(this.mensagemDeErro(err));
        },
      });
  }

  private mensagemDeErro(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Tente novamente.';
    }
    if (err.status === 409) {
      return err.error?.erro || 'CPF ou e-mail já utilizado em outro cadastro.';
    }
    return err.error?.erro || err.error?.message || 'Não foi possível enviar a solicitação.';
  }
}
