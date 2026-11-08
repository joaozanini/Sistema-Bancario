import Decimal from 'decimal.js';
import { DateTime } from 'luxon';

export interface HateoasLink {
  href: string;
}

export interface MovimentacaoDto {
  dataHora: string;
  tipo: string;
  cpfOrigem?: string | null;
  nomeOrigem?: string | null;
  cpfDestino?: string | null;
  nomeDestino?: string | null;
  valor: string;
}

export interface ExtratoDto {
  numeroConta: string;
  saldoAbertura: string;
  movimentacoes: MovimentacaoDto[];
  _links?: Record<string, HateoasLink>;
}

export type TipoMovimentacao = 'DEPOSITO' | 'SAQUE' | 'TRANSFERENCIA';

export type Natureza = 'ENTRADA' | 'SAIDA';

export interface Movimentacao {
  dataHora: DateTime;
  tipo: TipoMovimentacao;
  descricao: string;
  natureza: Natureza;
  contraparte: string | null;
  valor: Decimal;
  valorComSinal: Decimal;
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