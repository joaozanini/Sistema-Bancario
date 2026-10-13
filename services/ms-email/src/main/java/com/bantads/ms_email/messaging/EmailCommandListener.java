package com.bantads.ms_email.messaging;

import com.bantads.ms_email.config.RabbitConfig;
import com.bantads.ms_email.dto.ComandoEmail;
import com.bantads.ms_email.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor de ms.email.cmd. Fire-and-forget: nao publica em orquestrador.reply,
 * entao uma falha aqui nunca aborta a SAGA.
 *
 * Nenhum log inclui o payload: email.enviar-senha carrega a senha em claro, que
 * pelo enunciado nao pode aparecer em log, banco ou estado da SAGA.
 */
@Component
public class EmailCommandListener {

    private static final Logger log = LoggerFactory.getLogger(EmailCommandListener.class);

    private final JsonMapper jsonMapper;
    private final EmailService emailService;

    public EmailCommandListener(JsonMapper jsonMapper, EmailService emailService) {
        this.jsonMapper = jsonMapper;
        this.emailService = emailService;
    }

    @RabbitListener(queues = RabbitConfig.MS_EMAIL_CMD)
    public void onEmailCommand(Message message) {
        ComandoEmail comando;
        try {
            comando = jsonMapper.readValue(message.getBody(), ComandoEmail.class);
        } catch (JacksonException e) {
            // JSON invalido nao melhora com retentativa
            log.warn("[{}] mensagem descartada: JSON invalido", RabbitConfig.MS_EMAIL_CMD);
            return;
        }

        if (!emailService.sabeTratar(comando.tipo())) {
            log.warn("[{}] tipo desconhecido, descartado: {}", RabbitConfig.MS_EMAIL_CMD, comando.tipo());
            return;
        }

        // deixa a excecao subir: o retry da fila tenta de novo antes de descartar
        emailService.enviar(comando);
        log.info("[{}] e-mail '{}' enviado (saga {})", RabbitConfig.MS_EMAIL_CMD, comando.tipo(), comando.sagaId());
    }
}
