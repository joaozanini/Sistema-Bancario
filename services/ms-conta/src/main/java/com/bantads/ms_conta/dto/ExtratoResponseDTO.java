package com.bantads.ms_conta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtratoResponseDTO {

    private String numeroConta;
    private String saldoAbertura;
    private String saldo;
    private List<ItemExtratoDTO> movimentacoes = new ArrayList<>();

    @JsonProperty("_links")
    private Map<String, Map<String, String>> _links = new HashMap<>();

    public ExtratoResponseDTO() {
    }

    public ExtratoResponseDTO(
            String numeroConta,
            String saldoAbertura,
            String saldo,
            List<ItemExtratoDTO> movimentacoes
    ) {
        this.numeroConta = numeroConta;
        this.saldoAbertura = saldoAbertura;
        this.saldo = saldo;
        this.movimentacoes = movimentacoes != null ? movimentacoes : new ArrayList<>();
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

    public String getSaldoAbertura() {
        return saldoAbertura;
    }

    public void setSaldoAbertura(String saldoAbertura) {
        this.saldoAbertura = saldoAbertura;
    }

    public String getSaldo() {
        return saldo;
    }

    public void setSaldo(String saldo) {
        this.saldo = saldo;
    }

    public List<ItemExtratoDTO> getMovimentacoes() {
        return movimentacoes;
    }

    public void setMovimentacoes(List<ItemExtratoDTO> movimentacoes) {
        this.movimentacoes = movimentacoes;
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
