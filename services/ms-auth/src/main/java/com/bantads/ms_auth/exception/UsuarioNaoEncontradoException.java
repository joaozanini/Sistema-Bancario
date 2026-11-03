package com.bantads.ms_auth.exception;

public class UsuarioNaoEncontradoException extends RuntimeException {

    public UsuarioNaoEncontradoException() {
        super("Usuario nao encontrado");
    }
}
