import { SolicitacaoDto } from '../models/solicitacao.model';
import {
  filtrarPorStatus,
  normalizarStatus,
  ordenarSolicitacoes,
  paraSolicitacao,
} from './solicitacoes';

const base: SolicitacaoDto = {
  cpf: '12345678900',
  nome: 'Ana',
  salario: '4200.0000',
  status: 'PENDENTE',
};

describe('solicitacoes utils', () => {
  it('normaliza variações de status', () => {
    // a API responde APROVADA / NAO_APROVADA
    expect(normalizarStatus('APROVADA')).toBe('APROVADO');
    expect(normalizarStatus('NAO_APROVADA')).toBe('NAO_APROVADO');
    expect(normalizarStatus('Aprovado')).toBe('APROVADO');
    expect(normalizarStatus('Não aprovado')).toBe('NAO_APROVADO');
    expect(normalizarStatus('REJEITADO')).toBe('NAO_APROVADO');
    expect(normalizarStatus(null)).toBe('PENDENTE');
  });

  it('só mostra motivo e data nas processadas', () => {
    const pendente = paraSolicitacao({
      ...base,
      motivo: 'x',
      dataHoraProcessamento: '2026-10-10T10:00:00',
    });
    expect(pendente.motivo).toBeNull();
    expect(pendente.dataHora).toBeNull();

    const recusada = paraSolicitacao({
      ...base,
      status: 'NAO_APROVADA',
      motivo: 'Renda',
      dataHoraProcessamento: '2026-10-10T10:00:00',
    });
    expect(recusada.status).toBe('NAO_APROVADO');
    expect(recusada.motivo).toBe('Renda');
    expect(recusada.dataHora?.toFormat('dd/MM/yyyy HH:mm')).toBe('10/10/2026 10:00');
    expect(recusada.salario.toFixed(2)).toBe('4200.00');
  });

  it('ordena pendentes primeiro e filtra por status', () => {
    const lista = [
      paraSolicitacao({
        ...base,
        cpf: '11111111111',
        status: 'APROVADA',
        dataHoraProcessamento: '2026-10-01T10:00:00',
      }),
      paraSolicitacao({ ...base, cpf: '22222222222' }),
      paraSolicitacao({
        ...base,
        cpf: '33333333333',
        status: 'APROVADA',
        dataHoraProcessamento: '2026-10-05T10:00:00',
      }),
    ].sort(ordenarSolicitacoes);

    // o id da linha é o CPF, que é a chave da solicitação na API
    expect(lista.map((s) => s.id)).toEqual(['22222222222', '33333333333', '11111111111']);
    expect(filtrarPorStatus(lista, 'APROVADO')).toHaveLength(2);
    expect(filtrarPorStatus(lista, 'TODAS')).toHaveLength(3);
  });
});
