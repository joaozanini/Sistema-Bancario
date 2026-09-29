import Decimal from 'decimal.js';
import { DateTime } from 'luxon';

export interface HateoasLink {
  href: string;
}

export interface SolicitacaoDto {
  id: string;
  cpf: string;
  nome: string;
  email?: string;
  salario: string;
  status: string; // "PENDENTE" | "APROVADO" | "NAO_APROVADO"
  motivoRejeicao?: string | null;
  dataAprovacaoRejeicao?: string | null; // ISO 8601
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
