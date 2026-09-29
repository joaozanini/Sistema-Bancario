import { MoneyDecimal } from '../../../shared/utils/money.util';
import { parseAppDateTime } from '../../../shared/utils/date-time.util';
import {
  FiltroStatus,
  Solicitacao,
  SolicitacaoDto,
  StatusSolicitacao,
} from '../models/solicitacao.model';

export const ROTULO_STATUS: Record<StatusSolicitacao, string> = {
  PENDENTE: 'Pendente',
  APROVADO: 'Aprovado',
  NAO_APROVADO: 'Não Aprovado',
};

export const FILTROS: { valor: FiltroStatus; rotulo: string }[] = [
  { valor: 'TODAS', rotulo: 'Todas' },
  { valor: 'PENDENTE', rotulo: 'Pendentes' },
  { valor: 'APROVADO', rotulo: 'Aprovadas' },
  { valor: 'NAO_APROVADO', rotulo: 'Recusadas' },
];

export function normalizarStatus(status: string | null | undefined): StatusSolicitacao {
  const valor = (status ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .trim()
    .toUpperCase()
    .replace(/[\s-]+/g, '_');

  if (valor.startsWith('APROVAD')) {
    return 'APROVADO';
  }
  if (
    valor.startsWith('NAO_APROVAD') ||
    valor.startsWith('REJEITAD') ||
    valor.startsWith('RECUSAD') ||
    valor.startsWith('REPROVAD')
  ) {
    return 'NAO_APROVADO';
  }
  return 'PENDENTE';
}

export function paraSolicitacao(dto: SolicitacaoDto): Solicitacao {
  const status = normalizarStatus(dto.status);

  return {
    id: dto.id,
    cpf: dto.cpf,
    nome: dto.nome,
    salario: new MoneyDecimal(dto.salario || '0'),
    status,
    motivo: status === 'NAO_APROVADO' ? dto.motivoRejeicao?.trim() || null : null,
    dataHora: status === 'PENDENTE' ? null : parseAppDateTime(dto.dataAprovacaoRejeicao),
  };
}

export function ordenarSolicitacoes(a: Solicitacao, b: Solicitacao): number {
  const aPendente = a.status === 'PENDENTE';
  const bPendente = b.status === 'PENDENTE';

  if (aPendente !== bPendente) {
    return aPendente ? -1 : 1;
  }
  if (aPendente && bPendente) {
    return a.nome.localeCompare(b.nome, 'pt-BR');
  }
  return (b.dataHora?.toMillis() ?? 0) - (a.dataHora?.toMillis() ?? 0);
}

export function filtrarPorStatus(lista: Solicitacao[], filtro: FiltroStatus): Solicitacao[] {
  return filtro === 'TODAS' ? lista : lista.filter((s) => s.status === filtro);
}
