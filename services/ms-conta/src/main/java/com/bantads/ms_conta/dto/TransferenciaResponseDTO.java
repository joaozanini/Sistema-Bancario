package com.bantads.ms_conta.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class TransferenciaResponseDTO {

    private String idOrigem;
    private String idDestino;
    private String contaOrigem;
    private String contaDestino;
    private String valor;
    private LocalDateTime dataHora;
    private String mensagem;
    // R6: titular e nome de cada lado (os nomes chegam enriquecidos pelo Gateway)
    private Participante origem;
    private Participante destino;

    @JsonProperty("_links")
    private Map<String, Map<String, String>> _links = new HashMap<>();

    public TransferenciaResponseDTO() {
    }

    public void addLink(String rel, String href) {
        Map<String, String> link = new HashMap<>();
        link.put("href", href);
        this._links.put(rel, link);
    }

    public String getIdOrigem() {
        return idOrigem;
    }

    public void setIdOrigem(String idOrigem) {
        this.idOrigem = idOrigem;
    }

    public String getIdDestino() {
        return idDestino;
    }

    public void setIdDestino(String idDestino) {
        this.idDestino = idDestino;
    }

    public String getContaOrigem() {
        return contaOrigem;
    }

    public void setContaOrigem(String contaOrigem) {
        this.contaOrigem = contaOrigem;
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

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public Participante getOrigem() {
        return origem;
    }

    public void setOrigem(Participante origem) {
        this.origem = origem;
    }

    public Participante getDestino() {
        return destino;
    }

    public void setDestino(Participante destino) {
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

    public static class Participante {
        private String numero;
        private String cpf;
        private String nome;

        public Participante() {
        }

        public Participante(String numero, String cpf, String nome) {
            this.numero = numero;
            this.cpf = cpf;
            this.nome = nome;
        }

        public String getNumero() {
            return numero;
        }

        public void setNumero(String numero) {
            this.numero = numero;
        }

        public String getCpf() {
            return cpf;
        }

        public void setCpf(String cpf) {
            this.cpf = cpf;
        }

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }
    }
}
