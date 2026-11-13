package com.bantads.ms_cliente.dto;

// Endereço aninhado, igual no autocadastro (R1) e nos dados do cliente
public record EnderecoDTO(
        String logradouro,
        String numero,
        String complemento,
        String cep,
        String cidade,
        String uf) {
}
