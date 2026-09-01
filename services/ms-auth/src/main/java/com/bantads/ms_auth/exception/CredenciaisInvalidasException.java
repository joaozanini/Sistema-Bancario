package com.bantads.ms_auth.exception;

public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Login ou senha invalidos");
    }
}
