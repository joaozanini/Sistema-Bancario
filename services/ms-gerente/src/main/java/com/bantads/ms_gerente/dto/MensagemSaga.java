package com.bantads.ms_gerente.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

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
