package com.bantads.ms_gerente.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AtualizacaoGerenteDTO(

        @NotBlank(message = "Nome é obrigatório.")
        String nome,

        @NotBlank(message = "E-mail é obrigatório.")
        @Email(message = "E-mail inválido.")
        String email,

        String telefone) {
}
