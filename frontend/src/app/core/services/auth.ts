import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface LoginResponse {
  token?: string;
  perfil: string;
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private http = inject(HttpClient);

  private readonly loginUrl = 'http://localhost:3000/auth/login';

  login(email: string, senha: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(this.loginUrl, {
      email,
      senha,
    });
  }

  salvarToken(token: string) {
    localStorage.setItem('token', token);
  }
}