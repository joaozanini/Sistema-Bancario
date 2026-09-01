import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../../core/services/auth';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
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
        if (resposta.token) {
          this.auth.salvarToken(resposta.token);
        }

        this.redirecionar(resposta.perfil);
      },
      error: () => {
        this.erro = 'Não foi possível realizar o login.';
        this.carregando = false;
      },
    });
  }

  private redirecionar(perfil: string) {
    const destino = perfil.toLowerCase();

    if (['cliente', 'gerente', 'admin'].includes(destino)) {
      this.router.navigate([`/${destino}`]);
      return;
    }

    this.erro = 'Perfil de usuário inválido.';
    this.carregando = false;
  }
}