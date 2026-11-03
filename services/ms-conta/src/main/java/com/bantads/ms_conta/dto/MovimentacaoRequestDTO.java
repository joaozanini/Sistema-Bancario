package com.bantads.ms_conta.dto;

public class MovimentacaoRequestDTO {

    private String valor;

    public MovimentacaoRequestDTO() {
    }

    public MovimentacaoRequestDTO(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }
}
