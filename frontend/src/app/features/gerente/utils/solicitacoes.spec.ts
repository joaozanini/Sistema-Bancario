import { SolicitacaoDto } from '../models/solicitacao.model';
import {
  filtrarPorStatus,
  normalizarStatus,
  ordenarSolicitacoes,
  paraSolicitacao,
} from './solicitacoes';

const base: SolicitacaoDto = {
  id: '1',
  cpf: '12345678900',
  nome: 'Ana',
  salario: '4200.0000',
  status: 'PENDENTE',
};

describe('solicitacoes utils', () => {
  it('normaliza variações de status', () => {
    expect(normalizarStatus('Aprovado')).toBe('APROVADO');
    expect(normalizarStatus('Não aprovado')).toBe('NAO_APROVADO');
    expect(normalizarStatus('REJEITADO')).toBe('NAO_APROVADO');
    expect(normalizarStatus(null)).toBe('PENDENTE');
  });

  it('só mostra motivo e data nas processadas', () => {
    const pendente = paraSolicitacao({
      ...base,
      motivoRejeicao: 'x',
      dataAprovacaoRejeicao: '2026-10-10T10:00:00-03:00',
    });
    expect(pendente.motivo).toBeNull();
    expect(pendente.dataHora).toBeNull();

    const recusada = paraSolicitacao({
      ...base,
      status: 'NAO_APROVADO',
      motivoRejeicao: 'Renda',
      dataAprovacaoRejeicao: '2026-10-10T10:00:00-03:00',
    });
    expect(recusada.motivo).toBe('Renda');
    expect(recusada.dataHora?.toFormat('dd/MM/yyyy HH:mm')).toBe('10/10/2026 10:00');
    expect(recusada.salario.toFixed(2)).toBe('4200.00');
  });

  it('ordena pendentes primeiro e filtra por status', () => {
    const lista = [
      paraSolicitacao({
        ...base,
        id: 'a',
        status: 'APROVADO',
        dataAprovacaoRejeicao: '2026-10-01T10:00:00-03:00',
      }),
      paraSolicitacao({ ...base, id: 'b' }),
      paraSolicitacao({
        ...base,
        id: 'c',
        status: 'APROVADO',
        dataAprovacaoRejeicao: '2026-10-05T10:00:00-03:00',
      }),
    ].sort(ordenarSolicitacoes);

    expect(lista.map((s) => s.id)).toEqual(['b', 'c', 'a']);
    expect(filtrarPorStatus(lista, 'APROVADO')).toHaveLength(2);
    expect(filtrarPorStatus(lista, 'TODAS')).toHaveLength(3);
  });
});
