package com.bantads.ms_orquestrador.model;

import java.util.Map;

public record EstadoSaga(
        String sagaId,
        String tipo,
        int etapaAtual,
        StatusSaga status,
        Map<String, Object> payload,
        String timestamp) {

    public EstadoSaga comEtapa(int etapa, Map<String, Object> novoPayload, String agora) {
        return new EstadoSaga(sagaId, tipo, etapa, status, novoPayload, agora);
    }

    public EstadoSaga comStatus(StatusSaga novoStatus, String agora) {
        return new EstadoSaga(sagaId, tipo, etapaAtual, novoStatus, payload, agora);
    }
}
