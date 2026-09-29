import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type TipoUsuario = 'CLIENTE' | 'GERENTE' | 'ADMIN';

export interface UsuarioLogado {
  cpf: string;
  nome: string;
  email: string;
}

export interface LoginResponse {
  auth: boolean;
  token: string;
  tipo: TipoUsuario;
  usuario: UsuarioLogado;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private http = inject(HttpClient);

  private readonly loginUrl = 'http://localhost:3000/auth/login';

  login(email: string, senha: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(this.loginUrl, { email, senha });
  }

  salvarToken(token: string) {
    localStorage.setItem('token', token);
  }

  obterToken() {
    return localStorage.getItem('token');
  }

  salvarUsuario(usuario: UsuarioLogado) {
    localStorage.setItem('usuario', JSON.stringify(usuario));
  }

  obterUsuario(): UsuarioLogado | null {
    const usuario = localStorage.getItem('usuario');
    return usuario ? (JSON.parse(usuario) as UsuarioLogado) : null;
  }

  salvarTipo(tipo: TipoUsuario) {
    localStorage.setItem('tipo', tipo);
  }

  obterTipo(): TipoUsuario | null {
    const tipo = (localStorage.getItem('tipo') ?? '').toUpperCase();
    return tipo === 'CLIENTE' || tipo === 'GERENTE' || tipo === 'ADMIN' ? tipo : null;
  }

  estaAutenticado() {
    return !!this.obterToken();
  }

  logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('usuario');
    localStorage.removeItem('tipo');
  }
}