package com.bantads.ms_auth.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope das filas da SAGA (enunciado 5.7), igual ao do orquestrador.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MensagemSaga(
        String sagaId,
        String tipo,
        String timestamp,
        Map<String, Object> payload,
        StatusResposta status,
        String erro) {

    public static MensagemSaga sucesso(String sagaId, String tipo, String timestamp, Map<String, Object> payload) {
        return new MensagemSaga(sagaId, tipo, timestamp, payload, StatusResposta.SUCESSO, null);
    }

    public static MensagemSaga falha(String sagaId, String tipo, String timestamp, String erro) {
        return new MensagemSaga(sagaId, tipo, timestamp, Map.of(), StatusResposta.FALHA, erro);
    }
}
