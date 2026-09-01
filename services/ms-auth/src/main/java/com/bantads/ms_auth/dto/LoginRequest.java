package com.bantads.ms_auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Login e obrigatorio")
        String login,

        @NotBlank(message = "Senha e obrigatoria")
        String senha) {
}
