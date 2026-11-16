package com.bantads.ms_cliente.exception;

// R1: CPF ou e-mail já cadastrado
public class CadastroDuplicadoException extends IllegalArgumentException {
    public CadastroDuplicadoException(String mensagem) {
        super(mensagem);
    }
}
