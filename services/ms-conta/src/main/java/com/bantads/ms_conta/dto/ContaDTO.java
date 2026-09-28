package com.bantads.ms_conta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class ContaDTO {

    private String numeroConta;
    private String cpfCliente;
    private String gerenteCpf;

    @JsonProperty("_links")
    private Map<String, Map<String, String>> _links = new HashMap<>();

    public ContaDTO() {
    }

    public ContaDTO(String numeroConta, String cpfCliente, String gerenteCpf) {
        this.numeroConta = numeroConta;
        this.cpfCliente = cpfCliente;
        this.gerenteCpf = gerenteCpf;
    }

    public void addLink(String rel, String href) {
        Map<String, String> link = new HashMap<>();
        link.put("href", href);
        this._links.put(rel, link);
    }

    public String getNumeroConta() {
        return numeroConta;
    }

    public void setNumeroConta(String numeroConta) {
        this.numeroConta = numeroConta;
    }

    public String getCpfCliente() {
        return cpfCliente;
    }

    public void setCpfCliente(String cpfCliente) {
        this.cpfCliente = cpfCliente;
    }

    public String getGerenteCpf() {
        return gerenteCpf;
    }

    public void setGerenteCpf(String gerenteCpf) {
        this.gerenteCpf = gerenteCpf;
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
