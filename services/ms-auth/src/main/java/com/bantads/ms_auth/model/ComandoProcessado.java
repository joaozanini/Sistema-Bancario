package com.bantads.ms_auth.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.bantads.ms_auth.dto.StatusResposta;

/**
 * Registro de deduplicacao dos comandos de SAGA por (sagaId, tipo).
 * Guarda so o desfecho: a senha gerada nunca e persistida.
 */
@Document(collection = "comandos_processados")
@CompoundIndex(name = "saga_tipo", def = "{'sagaId': 1, 'tipo': 1}", unique = true)
public class ComandoProcessado {

    @Id
    private String id;

    private String sagaId;

    private String tipo;

    private StatusResposta status;

    private String erro;

    private Instant processadoEm;

    public ComandoProcessado() {
    }

    public ComandoProcessado(String sagaId, String tipo, StatusResposta status, String erro, Instant processadoEm) {
        this.sagaId = sagaId;
        this.tipo = tipo;
        this.status = status;
        this.erro = erro;
        this.processadoEm = processadoEm;
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

    public StatusResposta getStatus() {
        return status;
    }

    public void setStatus(StatusResposta status) {
        this.status = status;
    }

    public String getErro() {
        return erro;
    }

    public void setErro(String erro) {
        this.erro = erro;
    }

    public Instant getProcessadoEm() {
        return processadoEm;
    }

    public void setProcessadoEm(Instant processadoEm) {
        this.processadoEm = processadoEm;
    }
}
