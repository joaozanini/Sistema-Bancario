package com.bantads.ms_orquestrador.config;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declaracao das filas usadas pelo orquestrador da SAGA.
 */
@Configuration
public class RabbitMQConfig {

    public static final String FILA_SAGA_CMD = "saga.cmd";
    public static final String FILA_ORQUESTRADOR_REPLY = "orquestrador.reply";

    public static final String FILA_AUTH_CMD = "ms.auth.cmd";
    public static final String FILA_AUTH_CMD_DLQ = "ms.auth.cmd.dlq";

    public static final String FILA_CLIENTE_CMD = "ms.cliente.cmd";
    public static final String FILA_CLIENTE_CMD_DLQ = "ms.cliente.cmd.dlq";

    public static final String FILA_CONTA_CMD = "ms.conta.cmd";
    public static final String FILA_CONTA_CMD_DLQ = "ms.conta.cmd.dlq";

    public static final String FILA_GERENTE_CMD = "ms.gerente.cmd";
    public static final String FILA_GERENTE_CMD_DLQ = "ms.gerente.cmd.dlq";

    public static final String FILA_EMAIL_CMD = "ms.email.cmd";

    @Bean
    public Declarables filasSaga() {
        return new Declarables(
                filaSimples(FILA_SAGA_CMD),
                filaSimples(FILA_ORQUESTRADOR_REPLY),

                filaComDlq(FILA_AUTH_CMD, FILA_AUTH_CMD_DLQ),
                filaSimples(FILA_AUTH_CMD_DLQ),

                filaComDlq(FILA_CLIENTE_CMD, FILA_CLIENTE_CMD_DLQ),
                filaSimples(FILA_CLIENTE_CMD_DLQ),

                filaComDlq(FILA_CONTA_CMD, FILA_CONTA_CMD_DLQ),
                filaSimples(FILA_CONTA_CMD_DLQ),

                filaComDlq(FILA_GERENTE_CMD, FILA_GERENTE_CMD_DLQ),
                filaSimples(FILA_GERENTE_CMD_DLQ),

                filaSimples(FILA_EMAIL_CMD));
    }

    private Queue filaSimples(String nome) {
        return QueueBuilder.durable(nome).build();
    }

    private Queue filaComDlq(String nome, String nomeDlq) {
        return QueueBuilder.durable(nome)
                .deadLetterExchange("")
                .deadLetterRoutingKey(nomeDlq)
                .build();
    }
}
