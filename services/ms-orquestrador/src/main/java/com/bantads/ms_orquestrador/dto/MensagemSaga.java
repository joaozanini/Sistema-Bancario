package com.bantads.ms_orquestrador.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope de toda mensagem das filas da SAGA.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MensagemSaga(
        String sagaId,
        String tipo,
        String timestamp,
        Map<String, Object> payload,
        StatusResposta status,
        String erro) {

    public static MensagemSaga comando(String sagaId, String tipo, String timestamp, Map<String, Object> payload) {
        return new MensagemSaga(sagaId, tipo, timestamp, payload, null, null);
    }
}
