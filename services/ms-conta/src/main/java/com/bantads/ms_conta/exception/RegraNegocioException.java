package com.bantads.ms_conta.exception;

// Pedido bem formado que viola uma regra de negócio: transferir para a própria conta, conta destino
// inexistente (R6) ou extrato com mais de 365 dias (R7). Responde 422, como o saldo insuficiente
public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String message) {
        super(message);
    }
}
