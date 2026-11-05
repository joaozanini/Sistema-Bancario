import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../../core/services/auth';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  senha = '';
  erro = '';
  carregando = false;

  entrar() {
    if (!this.email || !this.senha) {
      this.erro = 'Preencha e-mail e senha.';
      return;
    }

    this.erro = '';
    this.carregando = true;

    this.auth.login(this.email, this.senha).subscribe({
            next: (resposta) => {
        this.auth.salvarToken(resposta.token);
        this.auth.salvarUsuario(resposta.usuario);
        this.auth.salvarTipo(resposta.tipo);
        this.redirecionar(resposta.tipo);
      },
      error: (err: HttpErrorResponse) => {
        this.erro = this.mensagemDeErro(err);
        this.carregando = false;
      },
    });
  }

   private redirecionar(tipo: string | undefined) {
    const destino = (tipo ?? '').toLowerCase();

    if (['cliente', 'gerente'].includes(destino)) {
      this.router.navigate([`/${destino}`]);
      return;
    }

    this.erro = 'Perfil de usuário inválido.';
    this.carregando = false;
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