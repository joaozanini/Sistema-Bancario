package com.bantads.ms_email.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fila de comando do MS Email. O broker também a carrega via
 * rabbitmq/definitions.json no boot; declará-la aqui (com os mesmos argumentos)
 * é idempotente e permite subir o serviço isoladamente com `mvnw spring-boot:run`.
 */
@Configuration
public class RabbitConfig {

    public static final String MS_EMAIL_CMD = "ms.email.cmd";

    @Bean
    Queue msEmailCmdQueue() {
        return QueueBuilder.durable(MS_EMAIL_CMD).build();
    }
}
