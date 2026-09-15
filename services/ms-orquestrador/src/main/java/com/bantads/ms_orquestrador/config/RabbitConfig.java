package com.bantads.ms_orquestrador.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filas base do Orquestrador. O broker também carrega estas filas via
 * rabbitmq/definitions.json no boot; declará-las aqui (com os mesmos argumentos)
 * é idempotente e permite subir o serviço isoladamente com `mvnw spring-boot:run`.
 * As filas de comando por MS (ms.*.cmd) e suas DLQs ficam só no definitions.json,
 * pois têm argumentos x-dead-letter-* que serão consumidos a partir da S6.
 */
@Configuration
public class RabbitConfig {

    public static final String SAGA_CMD = "saga.cmd";
    public static final String ORQUESTRADOR_REPLY = "orquestrador.reply";

    @Bean
    Queue sagaCmdQueue() {
        return QueueBuilder.durable(SAGA_CMD).build();
    }

    @Bean
    Queue orquestradorReplyQueue() {
        return QueueBuilder.durable(ORQUESTRADOR_REPLY).build();
    }
}
