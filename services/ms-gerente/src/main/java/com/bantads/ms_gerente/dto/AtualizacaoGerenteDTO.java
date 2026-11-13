package com.bantads.ms_gerente.dto;

import jakarta.validation.constraints.NotBlank;

// R14: só nome e telefone mudam. email e cpf são aceitos no corpo apenas para conferência:
// se vierem diferentes dos atuais a atualização é recusada, porque são imutáveis
public record AtualizacaoGerenteDTO(
        @NotBlank(message = "Nome é obrigatório.") String nome,
        @NotBlank(message = "Telefone é obrigatório.") String telefone,
        String email,
        String cpf) {

    public AtualizacaoGerenteDTO(String nome, String telefone) {
        this(nome, telefone, null, null);
    }
}
