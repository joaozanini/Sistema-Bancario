import Decimal from 'decimal.js';

export interface ClienteDto {
  cpf: string;
  nome: string;
  cidade: string | null;
  estado: string | null;
  saldo: string;
}

export interface ClientesListDto {
  clientes: ClienteDto[];
}

export interface Cliente {
  cpf: string;
  nome: string;
  cidade: string | null;
  estado: string | null;
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

// Resultado inline do job do relatório (GET /jobs/{jobId}/result)
export interface RelatorioClientesDto {
  clientes: LinhaRelatorioDto[];
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
