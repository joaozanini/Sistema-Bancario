import Decimal from 'decimal.js';
import { DateTime } from 'luxon';

/* ---------- DTOs (formato que vem do API Gateway) ---------- */

export interface HateoasLink {
  href: string;
}

export interface MovimentacaoDto {
  dataHora: string; // ISO 8601
  tipo: string; // "DEPOSITO" | "SAQUE" | "TRANSFERENCIA" (aceita acentos e os nomes dos eventos)
  cpfOrigem?: string | null;
  nomeOrigem?: string | null;
  cpfDestino?: string | null;
  nomeDestino?: string | null;
  valor: string; // monetário sempre como string
}

export interface ExtratoDto {
  numeroConta: string;
  saldoAbertura: string; // saldo consolidado ANTES da data inicial
  movimentacoes: MovimentacaoDto[];
  _links?: Record<string, HateoasLink>;
}

/* ---------- Modelo da tela ---------- */

export type TipoMovimentacao = 'DEPOSITO' | 'SAQUE' | 'TRANSFERENCIA';

export type Natureza = 'ENTRADA' | 'SAIDA';

export interface Movimentacao {
  dataHora: DateTime;
  tipo: TipoMovimentacao;
  descricao: string;
  natureza: Natureza;
  contraparte: string | null; // "De Fulano" / "Para Fulano" (só transferência)
  valor: Decimal; // sempre positivo
  valorComSinal: Decimal; // negativo quando é saída
}

export interface DiaExtrato {
  dia: DateTime;
  rotulo: string;
  movimentacoes: Movimentacao[];
  saldoDoDia: Decimal;
}

export interface LinhaDoTempo {
  saldoAbertura: Decimal;
  saldoFinal: Decimal;
  totalEntradas: Decimal;
  totalSaidas: Decimal;
  dias: DiaExtrato[];
}