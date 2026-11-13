package com.bantads.ms_cliente.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

// Listagem de solicitações (R8): a lista vai dentro de um objeto, que também leva os _links
public record SolicitacoesListDTO(
        List<SolicitacaoCadastroDTO> solicitacoes,
        @JsonProperty("_links") Map<String, Map<String, String>> _links) {
}
