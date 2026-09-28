package com.bantads.ms_conta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class TransferenciaResponseDTO {

    private String idOrigem;
    private String idDestino;
    private String contaOrigem;
    private String contaDestino;
    private String valor;
    private LocalDateTime dataHora;
    private String mensagem;

    @JsonProperty("_links")
    private Map<String, Map<String, String>> _links = new HashMap<>();

    public TransferenciaResponseDTO() {
    }

    public void addLink(String rel, String href) {
        Map<String, String> link = new HashMap<>();
        link.put("href", href);
        this._links.put(rel, link);
    }

    public String getIdOrigem() {
        return idOrigem;
    }

    public void setIdOrigem(String idOrigem) {
        this.idOrigem = idOrigem;
    }

    public String getIdDestino() {
        return idDestino;
    }

    public void setIdDestino(String idDestino) {
        this.idDestino = idDestino;
    }

    public String getContaOrigem() {
        return contaOrigem;
    }

    public void setContaOrigem(String contaOrigem) {
        this.contaOrigem = contaOrigem;
    }

    public String getContaDestino() {
        return contaDestino;
    }

    public void setContaDestino(String contaDestino) {
        this.contaDestino = contaDestino;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    @JsonProperty("_links")
    public Map<String, Map<String, String>> get_links() {
        return _links;
    }

    @JsonProperty("_links")
    public void set_links(Map<String, Map<String, String>> _links) {
        this._links = _links;
    }
}
