package com.bantads.ms_gerente.dto;

import jakarta.validation.constraints.NotBlank;

public record AtualizacaoGerenteDTO(
        @NotBlank(message = "Nome é obrigatório.") String nome,
        String telefone) {
}
