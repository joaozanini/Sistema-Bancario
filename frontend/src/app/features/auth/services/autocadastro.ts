import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

// Dados do formulário de autocadastro, como a tela os coleta
export interface SolicitacaoCadastroRequest {
  nome: string;
  cpf: string;
  email: string;
  telefone: string;
  salario: string;
  logradouro: string;
  numero: string;
  complemento: string;
  cep: string;
  cidade: string;
  uf: string;
}

export interface EnderecoDto {
  logradouro: string;
  numero: string;
  complemento: string | null;
  cep: string;
  cidade: string;
  uf: string;
}

// Corpo do POST /solicitacoes: o endereço vai aninhado
export interface AutocadastroDto {
  cpf: string;
  nome: string;
  email: string;
  telefone: string;
  salario: string;
  endereco: EnderecoDto;
}

// Resposta 201: a solicitação gravada, já com o status PENDENTE
export interface SolicitacaoCadastroResponse extends AutocadastroDto {
  status: string;
  motivo: string | null;
  dataHoraProcessamento: string | null;
}

export function paraAutocadastroDto(dados: SolicitacaoCadastroRequest): AutocadastroDto {
  const complemento = dados.complemento.trim();

  return {
    cpf: dados.cpf,
    nome: dados.nome,
    email: dados.email,
    telefone: dados.telefone,
    salario: dados.salario,
    endereco: {
      logradouro: dados.logradouro,
      numero: dados.numero,
      complemento: complemento || null,
      // o formulário usa a máscara 80000-000; a API recebe só os dígitos
      cep: dados.cep.replace(/\D/g, ''),
      cidade: dados.cidade,
      uf: dados.uf,
    },
  };
}

@Injectable({
  providedIn: 'root',
})
export class AutocadastroService {
  private http = inject(HttpClient);

  private readonly solicitacoesUrl = `${environment.apiUrl}/solicitacoes`;

  solicitar(dados: SolicitacaoCadastroRequest): Observable<SolicitacaoCadastroResponse> {
    return this.http.post<SolicitacaoCadastroResponse>(this.solicitacoesUrl, paraAutocadastroDto(dados));
  }
}
