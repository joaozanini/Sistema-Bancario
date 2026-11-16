package com.bantads.ms_conta.exception;

// Violação de regra de negócio (responde 422)
public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String message) {
        super(message);
    }
}
