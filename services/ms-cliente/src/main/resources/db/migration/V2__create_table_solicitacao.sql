CREATE TABLE solicitacao_cadastro (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome VARCHAR(100) NOT NULL,
    cpf VARCHAR(14) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    telefone VARCHAR(20),
    salario NUMERIC(19, 4) NOT NULL,
    logradouro VARCHAR(150) NOT NULL,
    numero VARCHAR(10) NOT NULL,
    complemento VARCHAR(100),
    cep VARCHAR(9) NOT NULL,
    cidade VARCHAR(100) NOT NULL,
    uf VARCHAR(2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    motivo_rejeicao TEXT,
    data_aprovacao_rejeicao TIMESTAMP
);