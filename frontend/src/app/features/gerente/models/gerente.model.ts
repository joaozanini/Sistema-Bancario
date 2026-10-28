export interface GerenteDto {
  cpf: string;
  nome: string;
  email: string;
  telefone: string | null;
  ativo: boolean;
  quantidadeClientes?: number; // vem da API Composition da listagem (R12)
}

// R14: CPF e e-mail (login) não podem ser alterados
export interface AtualizacaoGerente {
  nome: string;
  telefone: string;
}
