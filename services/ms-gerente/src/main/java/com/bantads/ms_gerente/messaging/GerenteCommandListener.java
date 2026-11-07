package com.bantads.ms_gerente.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.bantads.ms_gerente.config.RabbitMQConfig;
import com.bantads.ms_gerente.dto.MensagemSaga;
import com.bantads.ms_gerente.service.ComandoSagaService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GerenteCommandListener {

    private static final Logger log = LoggerFactory.getLogger(GerenteCommandListener.class);

    private final JsonMapper jsonMapper;
    private final ComandoSagaService service;
    private final RabbitTemplate rabbitTemplate;

    public GerenteCommandListener(JsonMapper jsonMapper, ComandoSagaService service, RabbitTemplate rabbitTemplate) {
        this.jsonMapper = jsonMapper;
        this.service = service;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.FILA_GERENTE_CMD)
    public void onComando(Message message) {
        MensagemSaga comando;
        try {
            comando = jsonMapper.readValue(message.getBody(), MensagemSaga.class);
        } catch (JacksonException e) {
            // JSON invalido nao melhora com retentativa
            log.warn("[{}] mensagem descartada: JSON invalido", RabbitMQConfig.FILA_GERENTE_CMD);
            return;
        }

        service.processar(comando).ifPresent(resposta -> {
            rabbitTemplate.send("", RabbitMQConfig.FILA_ORQUESTRADOR_REPLY, escrever(resposta));
            log.info("[{}] resposta enviada: sagaId={} tipo={} status={}", RabbitMQConfig.FILA_GERENTE_CMD,
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
