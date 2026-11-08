export interface GerenteDto {
  cpf: string;
  nome: string;
  email: string;
  telefone: string | null;
  ativo: boolean;
  quantidadeClientes?: number;
}

export interface AtualizacaoGerente {
  nome: string;
  telefone: string;
}

export interface NovoGerente {
  cpf: string;
  nome: string;
  email: string;
  telefone: string;
  senha: string;
}
