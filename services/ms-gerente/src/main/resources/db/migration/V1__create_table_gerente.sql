CREATE TABLE gerente (
    cpf VARCHAR(11) PRIMARY KEY,
    nome VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    telefone VARCHAR(20),
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

-- Gerentes pre-cadastrados (enunciado, secao 4)
INSERT INTO gerente (cpf, nome, email, telefone) VALUES
    ('98574307084', 'Geniéve', 'ger1@bantads.com.br', '41999990001'),
    ('64065268052', 'Godophredo', 'ger2@bantads.com.br', '41999990002'),
    ('23862179060', 'Gyândula', 'ger3@bantads.com.br', '41999990003'),
    ('40501740066', 'Gadamântio', 'ger4@bantads.com.br', '41999990004');
