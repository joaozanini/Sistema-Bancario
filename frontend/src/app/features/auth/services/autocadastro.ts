import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

export interface SolicitacaoCadastroRequest {
  nome: string;
  cpf: string;
  email: string;
  telefone: string;
  salario: string; // decimal em string, ex.: "2500.00"
  logradouro: string;
  numero: string;
  complemento: string;
  cep: string;
  cidade: string;
  uf: string;
}

export interface SolicitacaoCadastroResponse {
  mensagem: string;
  solicitacao: SolicitacaoCadastroRequest & { id: string; status: string };
}

@Injectable({
  providedIn: 'root',
})
export class AutocadastroService {
  private http = inject(HttpClient);

  private readonly solicitacoesUrl = `${environment.apiUrl}/solicitacoes`;

  solicitar(dados: SolicitacaoCadastroRequest): Observable<SolicitacaoCadastroResponse> {
    return this.http.post<SolicitacaoCadastroResponse>(this.solicitacoesUrl, dados);
  }
}
