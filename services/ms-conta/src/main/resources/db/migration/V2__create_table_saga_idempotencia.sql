CREATE TABLE IF NOT EXISTS saga_idempotencia (
    id VARCHAR(100) PRIMARY KEY,
    saga_id VARCHAR(100) NOT NULL,
    tipo VARCHAR(100) NOT NULL,
    resposta JSONB,
    timestamp TIMESTAMP NOT NULL,
    CONSTRAINT uk_saga_id_tipo UNIQUE (saga_id, tipo)
);
