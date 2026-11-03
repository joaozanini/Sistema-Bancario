package com.bantads.ms_auth.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.bantads.ms_auth.config.RabbitMQConfig;
import com.bantads.ms_auth.dto.MensagemSaga;
import com.bantads.ms_auth.service.ComandoSagaService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor de ms.auth.cmd. Erro de negocio volta como FALHA em
 * orquestrador.reply; erro tecnico sobe para o retry e, esgotado, vai para a DLQ.
 */
@Component
public class AuthCommandListener {

    private static final Logger log = LoggerFactory.getLogger(AuthCommandListener.class);

    private final JsonMapper jsonMapper;
    private final ComandoSagaService service;
    private final RabbitTemplate rabbitTemplate;

    public AuthCommandListener(JsonMapper jsonMapper, ComandoSagaService service, RabbitTemplate rabbitTemplate) {
        this.jsonMapper = jsonMapper;
        this.service = service;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.FILA_AUTH_CMD)
    public void onComando(Message message) {
        MensagemSaga comando;
        try {
            comando = jsonMapper.readValue(message.getBody(), MensagemSaga.class);
        } catch (JacksonException e) {
            // JSON invalido nao melhora com retentativa; o corpo nao e logado
            log.warn("[{}] mensagem descartada: JSON invalido", RabbitMQConfig.FILA_AUTH_CMD);
            return;
        }

        service.processar(comando).ifPresent(resposta -> {
            rabbitTemplate.send("", RabbitMQConfig.FILA_ORQUESTRADOR_REPLY, escrever(resposta));
            log.info("[{}] resposta enviada: sagaId={} tipo={} status={}", RabbitMQConfig.FILA_AUTH_CMD,
                    resposta.sagaId(), resposta.tipo(), resposta.status());
        });
    }

    private Message escrever(MensagemSaga resposta) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setContentEncoding("UTF-8");
        return new Message(jsonMapper.writeValueAsBytes(resposta), propriedades);
    }
}
