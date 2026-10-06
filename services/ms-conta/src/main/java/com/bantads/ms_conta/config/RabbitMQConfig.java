package com.bantads.ms_conta.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String FILA_CONTA_CMD = "ms.conta.cmd";
    public static final String FILA_CONTA_CMD_DLQ = "ms.conta.cmd.dlq";

    public static final String FILA_ORQUESTRADOR_REPLY = "orquestrador.reply";

    public static final String FILA_CONTA_EVENTS = "ms.conta.events";
    public static final String FILA_CONTA_EVENTS_DLQ = "ms.conta.events.dlq";

    @Bean
    public Declarables declarablesConta() {
        return new Declarables(
                QueueBuilder.durable(FILA_CONTA_CMD)
                        .deadLetterExchange("")
                        .deadLetterRoutingKey(FILA_CONTA_CMD_DLQ)
                        .build(),
                QueueBuilder.durable(FILA_CONTA_CMD_DLQ).build(),

                QueueBuilder.durable(FILA_ORQUESTRADOR_REPLY).build(),

                QueueBuilder.durable(FILA_CONTA_EVENTS)
                        .deadLetterExchange("")
                        .deadLetterRoutingKey(FILA_CONTA_EVENTS_DLQ)
                        .build(),
                QueueBuilder.durable(FILA_CONTA_EVENTS_DLQ).build()
        );
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
