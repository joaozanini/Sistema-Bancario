package com.bantads.ms_conta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Extrato (R7): saldo de abertura (anterior à data inicial) e movimentações do período. O saldo de cada
// dia não vem daqui: o front acumula sobre o saldoAbertura
public class ExtratoResponseDTO {

    private String numeroConta;
    private String dataInicio;
    private String dataFim;
    private String saldoAbertura;
    private List<ItemExtratoDTO> movimentacoes = new ArrayList<>();

    @JsonProperty("_links")
    private Map<String, Map<String, String>> _links = new LinkedHashMap<>();

    public ExtratoResponseDTO() {
    }

    public ExtratoResponseDTO(
            String numeroConta,
            String dataInicio,
            String dataFim,
            String saldoAbertura,
            List<ItemExtratoDTO> movimentacoes
    ) {
        this.numeroConta = numeroConta;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.saldoAbertura = saldoAbertura;
        this.movimentacoes = movimentacoes != null ? movimentacoes : new ArrayList<>();
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

    public String getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(String dataInicio) {
        this.dataInicio = dataInicio;
    }

    public String getDataFim() {
        return dataFim;
    }

    public void setDataFim(String dataFim) {
        this.dataFim = dataFim;
    }

    public String getSaldoAbertura() {
        return saldoAbertura;
    }

    public void setSaldoAbertura(String saldoAbertura) {
        this.saldoAbertura = saldoAbertura;
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
