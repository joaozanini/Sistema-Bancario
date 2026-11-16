package com.bantads.ms_cliente.dto;

// Endereço do cliente
public record EnderecoDTO(
        String logradouro,
        String numero,
        String complemento,
        String cep,
        String cidade,
        String uf) {
}
