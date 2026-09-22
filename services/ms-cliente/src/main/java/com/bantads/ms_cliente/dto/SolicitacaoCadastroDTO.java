package com.bantads.ms_cliente.dto;

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
    
    private Map<String, Map<String, String>> _links = new HashMap<>();
    
    public void addLink(String rel, String href) {
        Map<String, String> link = new HashMap<>();
        link.put("href", href);
        this._links.put(rel, link);
    }
}