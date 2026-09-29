CREATE TABLE cliente (
    cpf VARCHAR(11) PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    telefone VARCHAR(20),
    salario NUMERIC(19, 4) NOT NULL,
    logradouro VARCHAR(150) NOT NULL,
    numero VARCHAR(10) NOT NULL,
    complemento VARCHAR(100),
    cep VARCHAR(9) NOT NULL,
    cidade VARCHAR(100) NOT NULL,
    uf VARCHAR(2) NOT NULL
);

-- Clientes pre-cadastrados (enunciado, secao 4). O endereco fica a criterio da equipe.
INSERT INTO cliente (cpf, nome, email, telefone, salario, logradouro, numero, complemento, cep, cidade, uf) VALUES
    ('12912861012', 'Catharyna', 'cli1@bantads.com.br', '41988880001', 10000.00, 'Rua XV de Novembro', '100', NULL, '80020310', 'Curitiba', 'PR'),
    ('09506382000', 'Cleuddônio', 'cli2@bantads.com.br', '41988880002', 20000.00, 'Avenida Sete de Setembro', '200', 'Apto 12', '80230010', 'Curitiba', 'PR'),
    ('85733854057', 'Catianna', 'cli3@bantads.com.br', '41988880003', 3000.00, 'Rua Marechal Deodoro', '300', NULL, '80010010', 'Curitiba', 'PR'),
    ('58872160006', 'Cutardo', 'cli4@bantads.com.br', '41988880004', 500.00, 'Rua Comendador Araújo', '400', 'Casa', '80420000', 'Curitiba', 'PR'),
    ('76179646090', 'Coândrya', 'cli5@bantads.com.br', '41988880005', 1500.00, 'Avenida Visconde de Guarapuava', '500', NULL, '80060060', 'Curitiba', 'PR');
