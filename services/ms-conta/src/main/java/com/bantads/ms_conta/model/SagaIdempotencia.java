package com.bantads.ms_conta.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "saga_idempotencia")
public class SagaIdempotencia {

    @Id
    private String id;

    @Column(name = "saga_id", nullable = false)
    private String sagaId;

    @Column(nullable = false)
    private String tipo;

    @Column(columnDefinition = "jsonb")
    private String resposta;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public SagaIdempotencia() {
    }

    public SagaIdempotencia(String id, String sagaId, String tipo, String resposta, LocalDateTime timestamp) {
        this.id = id;
        this.sagaId = sagaId;
        this.tipo = tipo;
        this.resposta = resposta;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSagaId() {
        return sagaId;
    }

    public void setSagaId(String sagaId) {
        this.sagaId = sagaId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getResposta() {
        return resposta;
    }

    public void setResposta(String resposta) {
        this.resposta = resposta;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
