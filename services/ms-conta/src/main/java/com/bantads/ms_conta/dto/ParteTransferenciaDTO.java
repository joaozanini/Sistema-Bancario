package com.bantads.ms_conta.dto;

// Um dos lados de uma transferência (R6): conta, CPF e nome do titular
public record ParteTransferenciaDTO(String numeroConta, String cpf, String nome) {
}
