package com.bantads.ms_conta.dto;

public class ItemExtratoDTO {

    private String dataHora;
    private String tipo;
    private String cpfOrigem;
    private String nomeOrigem;
    private String cpfDestino;
    private String nomeDestino;
    private String valor;

    public ItemExtratoDTO() {
    }

    public ItemExtratoDTO(
            String dataHora,
            String tipo,
            String cpfOrigem,
            String nomeOrigem,
            String cpfDestino,
            String nomeDestino,
            String valor
    ) {
        this.dataHora = dataHora;
        this.tipo = tipo;
        this.cpfOrigem = cpfOrigem;
        this.nomeOrigem = nomeOrigem;
        this.cpfDestino = cpfDestino;
        this.nomeDestino = nomeDestino;
        this.valor = valor;
    }

    public String getDataHora() {
        return dataHora;
    }

    public void setDataHora(String dataHora) {
        this.dataHora = dataHora;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getCpfOrigem() {
        return cpfOrigem;
    }

    public void setCpfOrigem(String cpfOrigem) {
        this.cpfOrigem = cpfOrigem;
    }

    public String getNomeOrigem() {
        return nomeOrigem;
    }

    public void setNomeOrigem(String nomeOrigem) {
        this.nomeOrigem = nomeOrigem;
    }

    public String getCpfDestino() {
        return cpfDestino;
    }

    public void setCpfDestino(String cpfDestino) {
        this.cpfDestino = cpfDestino;
    }

    public String getNomeDestino() {
        return nomeDestino;
    }

    public void setNomeDestino(String nomeDestino) {
        this.nomeDestino = nomeDestino;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }
}
