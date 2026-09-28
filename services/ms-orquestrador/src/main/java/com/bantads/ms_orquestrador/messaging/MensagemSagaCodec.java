package com.bantads.ms_orquestrador.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

import com.bantads.ms_orquestrador.dto.MensagemSaga;

import tools.jackson.databind.json.JsonMapper;

/**
 * Leitura e escrita explicitas do envelope.
 */
@Component
public class MensagemSagaCodec {

    private final JsonMapper jsonMapper;

    public MensagemSagaCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public MensagemSaga ler(Message message) {
        return jsonMapper.readValue(message.getBody(), MensagemSaga.class);
    }

    public Message escrever(MensagemSaga mensagem) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setContentEncoding("UTF-8");
        return new Message(jsonMapper.writeValueAsBytes(mensagem), propriedades);
    }
}
