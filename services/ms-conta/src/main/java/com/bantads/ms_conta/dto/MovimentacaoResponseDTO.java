package com.bantads.ms_conta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class MovimentacaoResponseDTO {

    private String id;
    private String numeroConta;
    private String tipo;
    private String valor;
    private String saldo;
    private LocalDateTime dataHora;
    private String mensagem;

    @JsonProperty("_links")
    private Map<String, Map<String, String>> _links = new HashMap<>();

    public MovimentacaoResponseDTO() {
    }

    public void addLink(String rel, String href) {
        Map<String, String> link = new HashMap<>();
        link.put("href", href);
        this._links.put(rel, link);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNumeroConta() {
        return numeroConta;
    }

    public void setNumeroConta(String numeroConta) {
        this.numeroConta = numeroConta;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public String getSaldo() {
        return saldo;
    }

    public void setSaldo(String saldo) {
        this.saldo = saldo;
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
