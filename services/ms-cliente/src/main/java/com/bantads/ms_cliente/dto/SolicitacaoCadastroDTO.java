package com.bantads.ms_cliente.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
public class SolicitacaoCadastroDTO {
    private UUID id;
    private String nome;
    private String cpf;
    private String email;
    private String telefone;
    private String salario; 
    
    private String logradouro;
    private String numero;
    private String complemento;
    private String cep;
    private String cidade;
    private String uf;
    
    private String status;
    private String motivoRejeicao;
    private LocalDateTime dataAprovacaoRejeicao;
    
    @JsonProperty("_links")
    private Map<String, Map<String, String>> _links = new HashMap<>();
    
    public void addLink(String rel, String href) {
        Map<String, String> link = new HashMap<>();
        link.put("href", href);
        this._links.put(rel, link);
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