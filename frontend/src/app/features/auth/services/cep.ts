import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

export const URL_VIACEP = 'https://viacep.com.br/ws';

export interface Endereco {
  logradouro: string;
  complemento: string;
  cidade: string;
  uf: string;
}

interface RespostaViaCep {
  logradouro?: string;
  complemento?: string;
  localidade?: string;
  uf?: string;
  erro?: boolean | string;
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

  buscar(cep: string): Observable<Endereco> {
    const digitos = cep.replace(/\D/g, '');

    return this.http.get<RespostaViaCep>(`${URL_VIACEP}/${digitos}/json/`).pipe(
      map((resposta) => {
        if (resposta.erro) {
          throw new CepNaoEncontradoError();
        }
        return {
          logradouro: resposta.logradouro ?? '',
          complemento: resposta.complemento ?? '',
          cidade: resposta.localidade ?? '',
          uf: resposta.uf ?? '',
        };
      }),
    );
  }
}
