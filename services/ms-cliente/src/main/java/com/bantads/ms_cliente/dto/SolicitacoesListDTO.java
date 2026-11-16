package com.bantads.ms_cliente.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

// R8: lista de solicitações com _links
public record SolicitacoesListDTO(
        List<SolicitacaoCadastroDTO> solicitacoes,
        @JsonProperty("_links") Map<String, Map<String, String>> _links) {
}
