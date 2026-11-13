package com.bantads.ms_conta.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

// Confirmação de depósito, saque ou transferência (R4, R5, R6). Não leva o novo saldo: o front
// reconsulta a conta pelo link "conta", porque a projeção do lado query é assíncrona
public class MovimentacaoResponseDTO {

    private String numeroConta;
    private String tipo;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dataHora;

    private String valor;

    // só nas transferências; nos nomes vem o que o Gateway enriqueceu a partir do MS Cliente
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ParteTransferenciaDTO destino;

    @JsonProperty("_links")
    private Map<String, Map<String, String>> _links = new LinkedHashMap<>();

    public MovimentacaoResponseDTO() {
    }

    public void addLink(String rel, String href) {
        this._links.put(rel, Map.of("href", href));
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

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public ParteTransferenciaDTO getDestino() {
        return destino;
    }

    public void setDestino(ParteTransferenciaDTO destino) {
        this.destino = destino;
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
