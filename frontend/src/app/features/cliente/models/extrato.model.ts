import Decimal from 'decimal.js';
import { DateTime } from 'luxon';

export interface HateoasLink {
  href: string;
}

// Um dos lados de uma transferência: conta, CPF e nome do titular
export interface ParteTransferenciaDto {
  numeroConta?: string | null;
  cpf?: string | null;
  nome?: string | null;
}

// origem e destino só vêm preenchidos nas transferências
export interface MovimentacaoDto {
  dataHora: string;
  tipo: string;
  valor: string;
  origem?: ParteTransferenciaDto | null;
  destino?: ParteTransferenciaDto | null;
}

export interface ExtratoDto {
  numeroConta: string;
  dataInicio?: string;
  dataFim?: string;
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