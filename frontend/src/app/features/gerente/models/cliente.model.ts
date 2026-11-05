import Decimal from 'decimal.js';

export interface ClienteDto {
  cpf: string;
  nome: string;
  cidade: string;
  uf: string;
  saldo: string;
}

export interface Cliente {
  cpf: string;
  nome: string;
  cidade: string;
  uf: string;
  saldo: Decimal;
}

export interface LinhaRelatorioDto {
  cpf: string;
  nome: string;
  email: string;
  salario: string;
  numeroConta: string;
  saldo: string;
  cpfGerente: string | null;
  nomeGerente: string | null;
}

export interface LinhaRelatorio {
  cpf: string;
  nome: string;
  email: string;
  salario: Decimal;
  numeroConta: string;
  saldo: Decimal;
  cpfGerente: string | null;
  nomeGerente: string | null;
}
