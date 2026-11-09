package com.bantads.ms_email.dto;

import java.util.List;
import java.util.Map;

/**
 * Envelope das mensagens de ms.email.cmd, no formato do enunciado 5.7.
 */
public record ComandoEmail(String sagaId, String tipo, String timestamp, Map<String, Object> payload) {

    public String texto(String chave) {
        Object valor = payload == null ? null : payload.get(chave);
        return valor == null ? null : valor.toString();
    }

    /**
     * Os avisos de gerente (R13 e R15) trazem uma lista de clientes a notificar,
     * diferente dos outros tipos, que tem um unico destinatario no payload.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> lista(String chave) {
        Object valor = payload == null ? null : payload.get(chave);
        return valor instanceof List<?> ? (List<Map<String, Object>>) valor : List.of();
    }
}
