package com.bantads.ms_email.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declara a fila de comando do MS Email */
@Configuration
public class RabbitConfig {

    public static final String MS_EMAIL_CMD = "ms.email.cmd";

    @Bean
    Queue msEmailCmdQueue() {
        return QueueBuilder.durable(MS_EMAIL_CMD).build();
    }
}
