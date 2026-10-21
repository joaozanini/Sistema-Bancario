package com.bantads.ms_auth.exception;

public class CpfJaCadastradoException extends RuntimeException {

    public CpfJaCadastradoException() {
        super("CPF ja possui registro de autenticacao");
    }
}
