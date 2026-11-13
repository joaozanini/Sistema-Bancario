import { HateoasLink } from './extrato.model';

// Conta como o API Gateway devolve em GET /clientes/{cpf}/conta e GET /contas/{numero}
export interface ContaDto {
  numero: string;
  cpfCliente: string;
  cpfGerente: string;
  saldo: string;
  dataCriacao: string | null;
  _links?: Record<string, HateoasLink>;
}
