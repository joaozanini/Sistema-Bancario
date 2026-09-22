import Decimal from 'decimal.js';
import { DateTime } from 'luxon';

import {
  APP_LOCALE,
  APP_TIME_ZONE,
  parseAppDateTime,
} from '../../../shared/utils/date-time.util';
import { MoneyDecimal } from '../../../shared/utils/money.util';
import {
  DiaExtrato,
  ExtratoDto,
  LinhaDoTempo,
  Movimentacao,
  MovimentacaoDto,
  Natureza,
  TipoMovimentacao,
} from '../models/extrato.model';

export const DIAS_PERIODO_PADRAO = 30;
export const MAX_DIAS_PERIODO = 365;

export function hoje(): DateTime {
  return DateTime.now().setZone(APP_TIME_ZONE).setLocale(APP_LOCALE).startOf('day');
}

export function periodoPadrao(): { inicio: DateTime; fim: DateTime } {
  const fim = hoje();
  return { inicio: fim.minus({ days: DIAS_PERIODO_PADRAO }), fim };
}

export function lerDataInput(valor: string): DateTime | null {
  if (!valor) {
    return null;
  }

  const data = DateTime.fromISO(valor, { zone: APP_TIME_ZONE });
  return data.isValid ? data.setLocale(APP_LOCALE).startOf('day') : null;
}

export function paraDataInput(data: DateTime): string {
  return data.toISODate() ?? '';
}

export function validarPeriodo(inicio: DateTime | null, fim: DateTime | null): string | null {
  if (!inicio || !fim) {
    return 'Informe a data de início e a data de fim.';
  }

  if (inicio > fim) {
    return 'A data de início deve ser anterior ou igual à data de fim.';
  }

  if (fim.diff(inicio, 'days').days > MAX_DIAS_PERIODO) {
    return `O período máximo é de ${MAX_DIAS_PERIODO} dias.`;
  }

  return null;
}

function normalizarTexto(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .replace(/[^A-Z]/g, '');
}

function soDigitos(cpf: string | null | undefined): string {
  return (cpf ?? '').replace(/\D/g, '');
}

function identificarTipo(tipoBruto: string): { tipo: TipoMovimentacao; natureza?: Natureza } | null {
  const tipo = normalizarTexto(tipoBruto);

  if (tipo.startsWith('DEPOSITO')) {
    return { tipo: 'DEPOSITO', natureza: 'ENTRADA' };
  }

  if (tipo.startsWith('SAQUE')) {
    return { tipo: 'SAQUE', natureza: 'SAIDA' };
  }

  if (tipo === 'TRANSFERENCIAORIGEM') {
    return { tipo: 'TRANSFERENCIA', natureza: 'SAIDA' };
  }

  if (tipo === 'TRANSFERENCIADESTINO') {
    return { tipo: 'TRANSFERENCIA', natureza: 'ENTRADA' };
  }

  if (tipo.startsWith('TRANSFERENCIA')) {
    return { tipo: 'TRANSFERENCIA' };
  }

  return null;
}

function naturezaTransferencia(dto: MovimentacaoDto, cpfUsuario: string | null): Natureza {
  const cpf = soDigitos(cpfUsuario);

  if (cpf && soDigitos(dto.cpfOrigem) === cpf) {
    return 'SAIDA';
  }

  if (cpf && soDigitos(dto.cpfDestino) === cpf) {
    return 'ENTRADA';
  }

  return new MoneyDecimal(dto.valor).isNegative() ? 'SAIDA' : 'ENTRADA';
}

const DESCRICOES: Record<TipoMovimentacao, string> = {
  DEPOSITO: 'Depósito',
  SAQUE: 'Saque',
  TRANSFERENCIA: 'Transferência',
};

export function converterMovimentacao(
  dto: MovimentacaoDto,
  cpfUsuario: string | null,
): Movimentacao | null {
  const dataHora = parseAppDateTime(dto.dataHora);
  const identificado = identificarTipo(dto.tipo ?? '');

  if (!dataHora || !identificado) {
    return null;
  }

  let valor: Decimal;
  try {
    valor = new MoneyDecimal(dto.valor).abs();
  } catch {
    return null;
  }

  const natureza = identificado.natureza ?? naturezaTransferencia(dto, cpfUsuario);

  let contraparte: string | null = null;
  if (identificado.tipo === 'TRANSFERENCIA') {
    const nome = natureza === 'SAIDA' ? dto.nomeDestino : dto.nomeOrigem;
    contraparte = `${natureza === 'SAIDA' ? 'Para' : 'De'} ${nome || 'cliente não informado'}`;
  }

  return {
    dataHora,
    tipo: identificado.tipo,
    descricao: DESCRICOES[identificado.tipo],
    natureza,
    contraparte,
    valor,
    valorComSinal: natureza === 'SAIDA' ? valor.negated() : valor,
  };
}

export function montarLinhaDoTempo(
  extrato: ExtratoDto,
  inicio: DateTime,
  fim: DateTime,
  cpfUsuario: string | null,
): LinhaDoTempo {
  const primeiroDia = inicio.setZone(APP_TIME_ZONE).setLocale(APP_LOCALE).startOf('day');
  const ultimoDia = fim.setZone(APP_TIME_ZONE).setLocale(APP_LOCALE).startOf('day');
  const limiteFinal = ultimoDia.endOf('day');

  const saldoAbertura = new MoneyDecimal(extrato.saldoAbertura || '0');

  const movimentacoes = (extrato.movimentacoes ?? [])
    .map((dto) => converterMovimentacao(dto, cpfUsuario))
    .filter((m): m is Movimentacao => m !== null)
    .filter((m) => m.dataHora >= primeiroDia && m.dataHora <= limiteFinal)
    .sort((a, b) => a.dataHora.toMillis() - b.dataHora.toMillis());

  const porDia = new Map<string, Movimentacao[]>();
  for (const mov of movimentacoes) {
    const chave = mov.dataHora.toISODate() ?? '';
    const lista = porDia.get(chave) ?? [];
    lista.push(mov);
    porDia.set(chave, lista);
  }

  let saldo: Decimal = saldoAbertura;
  let totalEntradas: Decimal = new MoneyDecimal(0);
  let totalSaidas: Decimal = new MoneyDecimal(0);
  const dias: DiaExtrato[] = [];

  for (let dia = primeiroDia; dia <= ultimoDia; dia = dia.plus({ days: 1 }).startOf('day')) {
    const movsDoDia = porDia.get(dia.toISODate() ?? '') ?? [];

    for (const mov of movsDoDia) {
      saldo = saldo.plus(mov.valorComSinal);

      if (mov.natureza === 'ENTRADA') {
        totalEntradas = totalEntradas.plus(mov.valor);
      } else {
        totalSaidas = totalSaidas.plus(mov.valor);
      }
    }

    dias.push({
      dia,
      rotulo: dia.toFormat('cccc, dd/MM/yyyy'),
      movimentacoes: movsDoDia,
      saldoDoDia: saldo,
    });
  }

  return {
    saldoAbertura,
    saldoFinal: saldo,
    totalEntradas,
    totalSaidas,
    dias,
  };
}