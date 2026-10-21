export interface GerenteDto {
  cpf: string;
  nome: string;
  email: string;
  telefone: string | null;
  ativo: boolean;
}

// R14: CPF e e-mail (login) não podem ser alterados
export interface AtualizacaoGerente {
  nome: string;
  telefone: string;
}
