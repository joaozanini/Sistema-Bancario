package com.bantads.ms_conta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

public class ContaDTO {

    private String numero;
    private String cpfCliente;
    private String cpfGerente;
    private String saldo;
    // só a data (AAAA-MM-DD): é assim que o enunciado apresenta a criação da conta
    private String dataCriacao;

    @JsonProperty("_links")
    private Map<String, Map<String, String>> _links = new LinkedHashMap<>();

    public ContaDTO() {
    }

    public ContaDTO(String numero, String cpfCliente, String cpfGerente) {
        this.numero = numero;
        this.cpfCliente = cpfCliente;
        this.cpfGerente = cpfGerente;
    }

    public void addLink(String rel, String href) {
        this._links.put(rel, Map.of("href", href));
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getCpfCliente() {
        return cpfCliente;
    }

    public void setCpfCliente(String cpfCliente) {
        this.cpfCliente = cpfCliente;
    }

    public String getCpfGerente() {
        return cpfGerente;
    }

    public void setCpfGerente(String cpfGerente) {
        this.cpfGerente = cpfGerente;
    }

    public String getSaldo() {
        return saldo;
    }

    public void setSaldo(String saldo) {
        this.saldo = saldo;
    }

    public String getDataCriacao() {
        return dataCriacao;
    }

    public void setDataCriacao(String dataCriacao) {
        this.dataCriacao = dataCriacao;
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
