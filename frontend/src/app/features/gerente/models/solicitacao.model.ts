import Decimal from 'decimal.js';
import { DateTime } from 'luxon';

export interface HateoasLink {
  href: string;
}

// Solicitação como o API Gateway devolve; a chave é o CPF
export interface SolicitacaoDto {
  cpf: string;
  nome: string;
  email?: string;
  salario: string;
  status: string;
  motivo?: string | null;
  dataHoraProcessamento?: string | null;
  _links?: Record<string, HateoasLink>;
}

export interface SolicitacoesListDto {
  solicitacoes: SolicitacaoDto[];
  _links?: Record<string, HateoasLink>;
}

export type StatusSolicitacao = 'PENDENTE' | 'APROVADO' | 'NAO_APROVADO';

export type FiltroStatus = 'TODAS' | StatusSolicitacao;

export interface Solicitacao {
  id: string;
  cpf: string;
  nome: string;
  salario: Decimal;
  status: StatusSolicitacao;
  motivo: string | null; 
  dataHora: DateTime | null; 
}
