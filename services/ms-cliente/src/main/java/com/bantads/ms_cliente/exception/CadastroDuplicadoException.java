package com.bantads.ms_cliente.exception;

// R1: CPF ou e-mail já usado. Estende IllegalArgumentException porque continua sendo dado de entrada
// inválido, mas o controller responde 409 (conflito) em vez de 400
public class CadastroDuplicadoException extends IllegalArgumentException {
    public CadastroDuplicadoException(String mensagem) {
        super(mensagem);
    }
}
