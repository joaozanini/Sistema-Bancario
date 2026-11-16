package com.bantads.ms_email.dto;

import java.util.List;
import java.util.Map;

/** Mensagem de ms.email.cmd */
public record ComandoEmail(String sagaId, String tipo, String timestamp, Map<String, Object> payload) {

    public String texto(String chave) {
        Object valor = payload == null ? null : payload.get(chave);
        return valor == null ? null : valor.toString();
    }

    /** Lê do payload a lista de clientes a notificar (R13, R15) */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> lista(String chave) {
        Object valor = payload == null ? null : payload.get(chave);
        return valor instanceof List<?> ? (List<Map<String, Object>>) valor : List.of();
    }
}
