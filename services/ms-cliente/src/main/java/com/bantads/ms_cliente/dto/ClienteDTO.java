package com.bantads.ms_cliente.dto;

import com.bantads.ms_cliente.model.Cliente;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

public record ClienteDTO(
        String cpf,
        String nome,
        String email,
        String telefone,
        String salario,
        String logradouro,
        String numero,
        String complemento,
        String cep,
        String cidade,
        String uf,
        @JsonProperty("_links") Map<String, Map<String, String>> _links) {

    // Enunciado 5.6: URL interna do MS; o Gateway reescreve o host para o dele
    private static final String BASE_URL = "http://localhost:8082/clientes";

    public static ClienteDTO de(Cliente cliente) {
        return new ClienteDTO(
                cliente.getCpf(),
                cliente.getNome(),
                cliente.getEmail(),
                cliente.getTelefone(),
                cliente.getSalario().setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                cliente.getLogradouro(),
                cliente.getNumero(),
                cliente.getComplemento(),
                cliente.getCep(),
                cliente.getCidade(),
                cliente.getUf(),
                criarLinks(cliente.getCpf()));
    }

    private static Map<String, Map<String, String>> criarLinks(String cpf) {
        Map<String, Map<String, String>> links = new LinkedHashMap<>();
        links.put("self", Map.of("href", BASE_URL + "/" + cpf));
        links.put("clientes", Map.of("href", BASE_URL));
        return links;
    }
}
