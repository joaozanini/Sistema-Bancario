package com.bantads.ms_gerente.dto;

import com.bantads.ms_gerente.model.Gerente;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

public record GerenteDTO(
        String cpf,
        String nome,
        String email,
        String telefone,
        boolean ativo,
        @JsonProperty("_links") Map<String, Map<String, String>> _links
) {

    public GerenteDTO(String cpf, String nome, String email, String telefone, boolean ativo) {
        this(cpf, nome, email, telefone, ativo, criarLinksPadrao(cpf));
    }

    public static GerenteDTO de(Gerente gerente) {
        return new GerenteDTO(
                gerente.getCpf(),
                gerente.getNome(),
                gerente.getEmail(),
                gerente.getTelefone(),
                gerente.isAtivo(),
                criarLinksPadrao(gerente.getCpf())
        );
    }

    public void addLink(String rel, String href) {
        if (this._links != null) {
            this._links.put(rel, Map.of("href", href));
        }
    }

    public static Map<String, Map<String, String>> criarLinksPadrao(String cpf) {
        Map<String, Map<String, String>> links = new LinkedHashMap<>();
        String cpfPath = cpf != null ? cpf : "";
        String baseUrl = "http://localhost:8083/gerentes";
        links.put("self", Map.of("href", baseUrl + "/" + cpfPath));
        links.put("gerentes", Map.of("href", baseUrl));
        links.put("atualizar", Map.of("href", baseUrl + "/" + cpfPath));
        links.put("remover", Map.of("href", baseUrl + "/" + cpfPath));
        return links;
    }
}
