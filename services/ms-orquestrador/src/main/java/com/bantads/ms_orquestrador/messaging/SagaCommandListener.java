package com.bantads.ms_orquestrador.messaging;

import com.bantads.ms_orquestrador.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Esqueleto dos consumers do Orquestrador. Por enquanto apenas registra as
 * mensagens recebidas — a lógica de SAGA (roteamento por "tipo", máquina de
 * estados no Redis, timeout de 30s e compensações) entra a partir da S6.
 */
@Component
public class SagaCommandListener {

    private static final Logger log = LoggerFactory.getLogger(SagaCommandListener.class);

    @RabbitListener(queues = RabbitConfig.SAGA_CMD)
    public void onSagaCommand(Message message) {
        log.info("[{}] mensagem recebida: {}", RabbitConfig.SAGA_CMD, new String(message.getBody()));
    }

    @RabbitListener(queues = RabbitConfig.ORQUESTRADOR_REPLY)
    public void onReply(Message message) {
        log.info("[{}] resposta recebida: {}", RabbitConfig.ORQUESTRADOR_REPLY, new String(message.getBody()));
    }
}
