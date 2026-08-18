CREATE TABLE eventos_conta (
    id VARCHAR(36) PRIMARY KEY,
    objeto_id VARCHAR(50) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    versao INTEGER NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    CONSTRAINT uk_conta_versao UNIQUE (objeto_id, versao)
);