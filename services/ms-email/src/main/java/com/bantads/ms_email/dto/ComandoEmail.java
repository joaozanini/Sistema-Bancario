package com.bantads.ms_email.dto;

import java.util.Map;

/**
 * Envelope das mensagens de ms.email.cmd, no formato do enunciado 5.7.
 */
public record ComandoEmail(String sagaId, String tipo, String timestamp, Map<String, Object> payload) {

    public String texto(String chave) {
        Object valor = payload == null ? null : payload.get(chave);
        return valor == null ? null : valor.toString();
    }
}
