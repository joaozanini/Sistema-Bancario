CREATE DATABASE IF NOT EXISTS sistema_bancario
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE sistema_bancario;

CREATE TABLE IF NOT EXISTS clientes (
    cpf        VARCHAR(11)  NOT NULL,
    nome       VARCHAR(100) NOT NULL,
    sobrenome  VARCHAR(100) NOT NULL DEFAULT '',
    rg         VARCHAR(10)  NOT NULL,
    endereco   VARCHAR(255) NOT NULL DEFAULT '',
    PRIMARY KEY (cpf),
    UNIQUE KEY uk_rg (rg)
);

CREATE TABLE IF NOT EXISTS conta_corrente (
    numero      INT         NOT NULL,
    saldo       DOUBLE      NOT NULL DEFAULT 0,
    limite      DOUBLE      NOT NULL DEFAULT 0,
    cpf_cliente VARCHAR(11) NOT NULL,
    PRIMARY KEY (numero),
    UNIQUE KEY uk_cc_cliente (cpf_cliente),
    CONSTRAINT fk_cc_cliente
        FOREIGN KEY (cpf_cliente)
        REFERENCES clientes(cpf)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS conta_investimento (
    numero          INT         NOT NULL,
    saldo           DOUBLE      NOT NULL DEFAULT 0,
    montante_minimo DOUBLE      NOT NULL DEFAULT 0,
    deposito_minimo DOUBLE      NOT NULL DEFAULT 0,
    cpf_cliente     VARCHAR(11) NOT NULL,
    PRIMARY KEY (numero),
    UNIQUE KEY uk_ci_cliente (cpf_cliente),
    CONSTRAINT fk_ci_cliente
        FOREIGN KEY (cpf_cliente)
        REFERENCES clientes(cpf)
        ON DELETE CASCADE
);
