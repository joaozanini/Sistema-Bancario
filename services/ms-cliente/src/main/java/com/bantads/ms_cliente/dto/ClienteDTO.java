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
        EnderecoDTO endereco,
        @JsonProperty("_links") Map<String, Map<String, String>> _links) {

    // URL interna do MS
    private static final String BASE_URL = "http://localhost:8082/clientes";

    public static ClienteDTO de(Cliente cliente) {
        return new ClienteDTO(
                cliente.getCpf(),
                cliente.getNome(),
                cliente.getEmail(),
                cliente.getTelefone(),
                cliente.getSalario().setScale(2, RoundingMode.HALF_EVEN).toPlainString(),
                new EnderecoDTO(
                        cliente.getLogradouro(),
                        cliente.getNumero(),
                        cliente.getComplemento(),
                        cliente.getCep(),
                        cliente.getCidade(),
                        cliente.getUf()),
                criarLinks(cliente.getCpf()));
    }

    // R3: link para a conta do cliente
    private static Map<String, Map<String, String>> criarLinks(String cpf) {
        Map<String, Map<String, String>> links = new LinkedHashMap<>();
        links.put("self", Map.of("href", BASE_URL + "/" + cpf));
        links.put("conta", Map.of("href", BASE_URL + "/" + cpf + "/conta"));
        return links;
    }
}
