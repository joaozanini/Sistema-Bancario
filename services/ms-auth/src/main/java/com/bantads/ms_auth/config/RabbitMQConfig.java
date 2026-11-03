package com.bantads.ms_auth.config;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mesmos argumentos de rabbitmq/definitions.json: o broker recusa redeclarar
 * uma fila com argumentos diferentes.
 */
@Configuration
public class RabbitMQConfig {

    public static final String FILA_AUTH_CMD = "ms.auth.cmd";
    public static final String FILA_AUTH_CMD_DLQ = "ms.auth.cmd.dlq";
    public static final String FILA_ORQUESTRADOR_REPLY = "orquestrador.reply";

    @Bean
    public Declarables filasAuth() {
        return new Declarables(
                QueueBuilder.durable(FILA_AUTH_CMD)
                        .deadLetterExchange("")
                        .deadLetterRoutingKey(FILA_AUTH_CMD_DLQ)
                        .build(),
                QueueBuilder.durable(FILA_AUTH_CMD_DLQ).build(),
                QueueBuilder.durable(FILA_ORQUESTRADOR_REPLY).build());
    }
}
