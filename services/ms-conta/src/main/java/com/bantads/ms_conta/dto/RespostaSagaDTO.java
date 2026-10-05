package com.bantads.ms_conta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RespostaSagaDTO {

    private String sagaId;
    private String tipo;
    private Map<String, Object> payload;
    private String timestamp;
    private String status; // "SUCESSO" | "FALHA"
    private String erro;

    public RespostaSagaDTO() {
    }

    public static RespostaSagaDTO sucesso(String sagaId, String tipo, Map<String, Object> payload) {
        RespostaSagaDTO resposta = new RespostaSagaDTO();
        resposta.setSagaId(sagaId);
        resposta.setTipo(tipo);
        resposta.setPayload(payload);
        resposta.setStatus("SUCESSO");
        resposta.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        return resposta;
    }

    public static RespostaSagaDTO falha(String sagaId, String tipo, String erro) {
        RespostaSagaDTO resposta = new RespostaSagaDTO();
        resposta.setSagaId(sagaId);
        resposta.setTipo(tipo);
        resposta.setStatus("FALHA");
        resposta.setErro(erro);
        resposta.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        return resposta;
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

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErro() {
        return erro;
    }

    public void setErro(String erro) {
        this.erro = erro;
    }
}
