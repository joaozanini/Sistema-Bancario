import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, throwError } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface Endereco {
  logradouro: string;
  complemento: string;
  cidade: string;
  uf: string;
}

export class CepNaoEncontradoError extends Error {
  constructor() {
    super('CEP não encontrado.');
  }
}

@Injectable({
  providedIn: 'root',
})
export class CepService {
  private http = inject(HttpClient);

  // A consulta passa pelo API Gateway (GET /cep/{cep}), que é quem fala com o ViaCEP:
  // o front só conversa com o Gateway
  buscar(cep: string): Observable<Endereco> {
    const digitos = cep.replace(/\D/g, '');

    return this.http.get<Partial<Endereco>>(`${environment.apiUrl}/cep/${digitos}`).pipe(
      map((resposta) => ({
        logradouro: resposta.logradouro ?? '',
        complemento: resposta.complemento ?? '',
        cidade: resposta.cidade ?? '',
        uf: resposta.uf ?? '',
      })),
      catchError((erro: HttpErrorResponse) =>
        throwError(() => (erro.status === 404 ? new CepNaoEncontradoError() : erro)),
      ),
    );
  }
}
