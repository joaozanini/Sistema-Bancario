package com.bantads.ms_auth.exception;

public class LoginJaCadastradoException extends RuntimeException {

    public LoginJaCadastradoException(String login) {
        super("Login ja cadastrado: " + login);
    }
}
