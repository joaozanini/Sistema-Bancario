import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../../core/services/auth';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private auth = inject(AuthService);

  email = '';
  senha = '';

  entrar() {
    this.auth.login(this.email, this.senha).subscribe({
      next: (res) => console.log('Login realizado', res),
      error: (err) => console.error('Erro no login', err),
    });
  }
}