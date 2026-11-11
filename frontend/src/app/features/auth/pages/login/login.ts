import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../../core/services/auth';
import { Logo } from '../../../../shared/components/logo/logo';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink, Logo],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  senha = '';
  // signals, e nao campos comuns: o app e zoneless, entao uma atribuicao dentro
  // do callback do subscribe nao agendaria a deteccao de mudancas e a tela ficaria
  // presa em "Entrando..." mesmo depois do erro chegar
  readonly erro = signal('');
  readonly carregando = signal(false);

  entrar() {
    if (!this.email || !this.senha) {
      this.erro.set('Preencha e-mail e senha.');
      return;
    }

    this.erro.set('');
    this.carregando.set(true);

    this.auth.login(this.email, this.senha).subscribe({
            next: (resposta) => {
        this.auth.salvarToken(resposta.token);
        this.auth.salvarUsuario(resposta.usuario);
        this.auth.salvarTipo(resposta.tipo);
        this.redirecionar(resposta.tipo);
      },
      error: (err: HttpErrorResponse) => {
        this.erro.set(this.mensagemDeErro(err));
        this.carregando.set(false);
      },
    });
  }

   private redirecionar(tipo: string | undefined) {
    const destino = (tipo ?? '').toLowerCase();

    if (['cliente', 'gerente'].includes(destino)) {
      this.router.navigate([`/${destino}`]);
      return;
    }

    this.erro.set('Perfil de usuário inválido.');
    this.carregando.set(false);
  }

  private mensagemDeErro(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Não foi possível conectar ao servidor. Tente novamente.';
    }
    if (err.status === 401) {
      return err.error?.message || 'Login inválido!';
    }
    return 'Não foi possível realizar o login.';
  }
}