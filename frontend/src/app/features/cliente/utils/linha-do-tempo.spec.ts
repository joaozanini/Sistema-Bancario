import { DateTime } from 'luxon';

import { APP_TIME_ZONE } from '../../../shared/utils/date-time.util';
import { ExtratoDto } from '../models/extrato.model';
import { montarLinhaDoTempo, validarPeriodo } from './linha-do-tempo';

const CATHARYNA = { numeroConta: '1291', cpf: '12912861012', nome: 'Catharyna' };
const CLEUDDONIO = { numeroConta: '0950', cpf: '09506382000', nome: 'Cleuddônio' };

const dia = (iso: string) => DateTime.fromISO(iso, { zone: APP_TIME_ZONE });

// movimentações do seed da conta 1291 em janeiro de 2020
const EXTRATO: ExtratoDto = {
  numeroConta: '1291',
  dataInicio: '2020-01-15',
  dataFim: '2020-01-20',
  saldoAbertura: '3000.00',
  movimentacoes: [
    { dataHora: '2020-01-15T08:00:00', tipo: 'SAQUE', valor: '500.00', origem: null, destino: null },
    {
      dataHora: '2020-01-20T12:00:00',
      tipo: 'TRANSFERENCIA',
      valor: '1700.00',
      origem: CATHARYNA,
      destino: CLEUDDONIO,
    },
  ],
};

describe('montarLinhaDoTempo', () => {
  it('mostra o saldo consolidado de todos os dias, mesmo os sem movimentação (R7)', () => {
    const linha = montarLinhaDoTempo(EXTRATO, dia('2020-01-15'), dia('2020-01-20'), CATHARYNA.cpf);

    expect(linha.dias.map((d) => d.dia.toISODate())).toEqual([
      '2020-01-15',
      '2020-01-16',
      '2020-01-17',
      '2020-01-18',
      '2020-01-19',
      '2020-01-20',
    ]);
    expect(linha.dias.map((d) => d.saldoDoDia.toFixed(2))).toEqual([
      '2500.00',
      '2500.00',
      '2500.00',
      '2500.00',
      '2500.00',
      '800.00',
    ]);
    expect(linha.saldoAbertura.toFixed(2)).toBe('3000.00');
    expect(linha.saldoFinal.toFixed(2)).toBe('800.00');
  });

  it('transferência é saída para quem enviou e mostra o nome do destino', () => {
    const linha = montarLinhaDoTempo(EXTRATO, dia('2020-01-20'), dia('2020-01-20'), CATHARYNA.cpf);
    const [transferencia] = linha.dias[0].movimentacoes;

    expect(transferencia.natureza).toBe('SAIDA');
    expect(transferencia.contraparte).toBe('Para Cleuddônio');
    expect(transferencia.valorComSinal.toFixed(2)).toBe('-1700.00');
  });

  it('a mesma transferência é entrada para quem recebeu e mostra o nome da origem', () => {
    const doDestino: ExtratoDto = { ...EXTRATO, numeroConta: '0950', saldoAbertura: '0.00' };
    const linha = montarLinhaDoTempo(doDestino, dia('2020-01-20'), dia('2020-01-20'), CLEUDDONIO.cpf);
    const [transferencia] = linha.dias[0].movimentacoes;

    expect(transferencia.natureza).toBe('ENTRADA');
    expect(transferencia.contraparte).toBe('De Catharyna');
    expect(linha.saldoFinal.toFixed(2)).toBe('1700.00');
  });

  it('depósito e saque não têm contraparte', () => {
    const linha = montarLinhaDoTempo(EXTRATO, dia('2020-01-15'), dia('2020-01-15'), CATHARYNA.cpf);
    const [saque] = linha.dias[0].movimentacoes;

    expect(saque.natureza).toBe('SAIDA');
    expect(saque.contraparte).toBeNull();
  });
});

describe('validarPeriodo', () => {
  it('aceita até 365 dias e recusa período maior ou invertido', () => {
    expect(validarPeriodo(dia('2020-01-01'), dia('2020-12-31'))).toBeNull();
    expect(validarPeriodo(dia('2020-01-01'), dia('2021-01-02'))).toContain('365');
    expect(validarPeriodo(dia('2020-02-01'), dia('2020-01-01'))).toContain('anterior');
  });
});
