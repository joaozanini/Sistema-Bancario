package com.bantads.ms_cliente.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String FILA_CLIENTE_CMD = "ms.cliente.cmd";
    public static final String FILA_CLIENTE_CMD_DLQ = "ms.cliente.cmd.dlq";
    public static final String FILA_ORQUESTRADOR_REPLY = "orquestrador.reply";

    @Bean
    public Declarables declarablesCliente() {
        return new Declarables(
                QueueBuilder.durable(FILA_CLIENTE_CMD)
                        .deadLetterExchange("")
                        .deadLetterRoutingKey(FILA_CLIENTE_CMD_DLQ)
                        .build(),
                QueueBuilder.durable(FILA_CLIENTE_CMD_DLQ).build(),
                QueueBuilder.durable(FILA_ORQUESTRADOR_REPLY).build()
        );
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
