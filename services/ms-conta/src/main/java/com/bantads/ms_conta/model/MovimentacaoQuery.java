package com.bantads.ms_conta.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimentacao_query")
public class MovimentacaoQuery {

    @Id
    @Column(nullable = false, length = 100)
    private String id;

    @Column(name = "numero_conta", nullable = false, length = 50)
    private String numeroConta;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false, length = 20)
    private String natureza;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(name = "cpf_origem", length = 20)
    private String cpfOrigem;

    @Column(name = "nome_origem", length = 150)
    private String nomeOrigem;

    @Column(name = "cpf_destino", length = 20)
    private String cpfDestino;

    @Column(name = "nome_destino", length = 150)
    private String nomeDestino;

    @Column(nullable = false)
    private Integer versao;

    public MovimentacaoQuery() {
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

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getNatureza() {
        return natureza;
    }

    public void setNatureza(String natureza) {
        this.natureza = natureza;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
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

    public Integer getVersao() {
        return versao;
    }

    public void setVersao(Integer versao) {
        this.versao = versao;
    }
}
