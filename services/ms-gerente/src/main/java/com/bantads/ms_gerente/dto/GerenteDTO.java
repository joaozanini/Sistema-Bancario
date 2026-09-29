package com.bantads.ms_gerente.dto;

import com.bantads.ms_gerente.model.Gerente;

public record GerenteDTO(String cpf, String nome, String email, String telefone, boolean ativo) {

    public static GerenteDTO de(Gerente gerente) {
        return new GerenteDTO(gerente.getCpf(), gerente.getNome(), gerente.getEmail(),
                gerente.getTelefone(), gerente.isAtivo());
    }
}
