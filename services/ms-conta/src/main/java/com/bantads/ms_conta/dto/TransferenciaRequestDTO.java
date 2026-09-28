package com.bantads.ms_conta.dto;

public class TransferenciaRequestDTO {

    private String contaDestino;
    private String valor;
    private String cpfDestino;
    private String nomeDestino;
    private String cpfOrigem;
    private String nomeOrigem;

    public TransferenciaRequestDTO() {
    }

    public TransferenciaRequestDTO(String contaDestino, String valor) {
        this.contaDestino = contaDestino;
        this.valor = valor;
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
}
