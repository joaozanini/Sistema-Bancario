package com.bantads.ms_gerente.dto;

import jakarta.validation.constraints.NotBlank;

// R14: dados da atualização do gerente
public record AtualizacaoGerenteDTO(
        @NotBlank(message = "Nome é obrigatório.") String nome,
        @NotBlank(message = "Telefone é obrigatório.") String telefone,
        String email,
        String cpf) {

    public AtualizacaoGerenteDTO(String nome, String telefone) {
        this(nome, telefone, null, null);
    }
}
