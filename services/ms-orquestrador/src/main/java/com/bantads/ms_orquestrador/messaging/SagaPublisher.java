package com.bantads.ms_orquestrador.messaging;

import com.bantads.ms_orquestrador.dto.MensagemSaga;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class SagaPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final MensagemSagaCodec codec;

    public SagaPublisher(RabbitTemplate rabbitTemplate, MensagemSagaCodec codec) {
        this.rabbitTemplate = rabbitTemplate;
        this.codec = codec;
    }

    public void publicar(String fila, MensagemSaga mensagem) {
        rabbitTemplate.send("", fila, codec.escrever(mensagem));
    }
}
