package com.bantads.ms_conta.dto;

// Linha do extrato (R7). origem e destino só vêm preenchidos nas transferências: o front compara o CPF
// do usuário logado com eles para saber se o valor entrou ou saiu
public class ItemExtratoDTO {

    private String dataHora;
    private String tipo;
    private String valor;
    private ParteTransferenciaDTO origem;
    private ParteTransferenciaDTO destino;

    public ItemExtratoDTO() {
    }

    public ItemExtratoDTO(
            String dataHora,
            String tipo,
            String valor,
            ParteTransferenciaDTO origem,
            ParteTransferenciaDTO destino
    ) {
        this.dataHora = dataHora;
        this.tipo = tipo;
        this.valor = valor;
        this.origem = origem;
        this.destino = destino;
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

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public ParteTransferenciaDTO getOrigem() {
        return origem;
    }

    public void setOrigem(ParteTransferenciaDTO origem) {
        this.origem = origem;
    }

    public ParteTransferenciaDTO getDestino() {
        return destino;
    }

    public void setDestino(ParteTransferenciaDTO destino) {
        this.destino = destino;
    }
}
