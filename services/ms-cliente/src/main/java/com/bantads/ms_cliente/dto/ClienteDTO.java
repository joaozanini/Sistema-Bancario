package com.bantads.ms_cliente.dto;

import com.bantads.ms_cliente.model.Cliente;

import java.math.RoundingMode;

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
        String uf) {

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
                cliente.getUf());
    }
}
