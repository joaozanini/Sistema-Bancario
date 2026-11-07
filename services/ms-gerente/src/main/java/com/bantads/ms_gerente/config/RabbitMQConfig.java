package com.bantads.ms_gerente.config;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String FILA_GERENTE_CMD = "ms.gerente.cmd";
    public static final String FILA_GERENTE_CMD_DLQ = "ms.gerente.cmd.dlq";
    public static final String FILA_ORQUESTRADOR_REPLY = "orquestrador.reply";

    // mesmos argumentos de rabbitmq/definitions.json: o broker recusa redeclarar com argumentos diferentes
    @Bean
    public Declarables filasGerente() {
        return new Declarables(
                QueueBuilder.durable(FILA_GERENTE_CMD)
                        .deadLetterExchange("")
                        .deadLetterRoutingKey(FILA_GERENTE_CMD_DLQ)
                        .build(),
                QueueBuilder.durable(FILA_GERENTE_CMD_DLQ).build(),
                QueueBuilder.durable(FILA_ORQUESTRADOR_REPLY).build());
    }
}
