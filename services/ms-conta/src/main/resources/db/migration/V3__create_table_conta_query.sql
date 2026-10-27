CREATE TABLE IF NOT EXISTS conta_query (
    numero_conta VARCHAR(50) PRIMARY KEY,
    cpf_cliente VARCHAR(20) NOT NULL,
    gerente_cpf VARCHAR(20) NOT NULL,
    saldo NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    limite NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    salario NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    data_criacao TIMESTAMP NOT NULL,
    ultima_versao INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_conta_query_cpf_cliente ON conta_query (cpf_cliente);
CREATE INDEX IF NOT EXISTS idx_conta_query_gerente_cpf ON conta_query (gerente_cpf);

CREATE TABLE IF NOT EXISTS movimentacao_query (
    id VARCHAR(100) PRIMARY KEY,
    numero_conta VARCHAR(50) NOT NULL,
    data_hora TIMESTAMP NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    natureza VARCHAR(20) NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    cpf_origem VARCHAR(20),
    nome_origem VARCHAR(150),
    cpf_destino VARCHAR(20),
    nome_destino VARCHAR(150),
    versao INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_movimentacao_query_conta_data ON movimentacao_query (numero_conta, data_hora);
