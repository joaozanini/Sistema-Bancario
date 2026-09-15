package com.bantads.ms_email.messaging;

import com.bantads.ms_email.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Esqueleto do consumer do MS Email. A fila ms.email.cmd é fire-and-forget
 * (sem resposta em orquestrador.reply). Por enquanto apenas registra a
 * mensagem — o envio real via SMTP do Gmail entra na S6.
 */
@Component
public class EmailCommandListener {

    private static final Logger log = LoggerFactory.getLogger(EmailCommandListener.class);

    @RabbitListener(queues = RabbitConfig.MS_EMAIL_CMD)
    public void onEmailCommand(Message message) {
        log.info("[{}] pedido de e-mail recebido: {}", RabbitConfig.MS_EMAIL_CMD, new String(message.getBody()));
    }
}
