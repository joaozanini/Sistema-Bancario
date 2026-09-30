package com.bantads.ms_orquestrador.messaging;

import com.bantads.ms_orquestrador.config.RabbitMQConfig;
import com.bantads.ms_orquestrador.dto.MensagemSaga;
import com.bantads.ms_orquestrador.service.SagaService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

@Component
public class SagaCommandListener {

    private static final Logger log = LoggerFactory.getLogger(SagaCommandListener.class);

    private final MensagemSagaCodec codec;
    private final SagaService sagaService;

    public SagaCommandListener(MensagemSagaCodec codec, SagaService sagaService) {
        this.codec = codec;
        this.sagaService = sagaService;
    }

    @RabbitListener(queues = RabbitMQConfig.FILA_SAGA_CMD)
    public void onSagaCommand(Message message) {
        ler(message, RabbitMQConfig.FILA_SAGA_CMD).ifPresent(sagaService::iniciar);
    }

    @RabbitListener(queues = RabbitMQConfig.FILA_ORQUESTRADOR_REPLY)
    public void onReply(Message message) {
        ler(message, RabbitMQConfig.FILA_ORQUESTRADOR_REPLY).ifPresent(sagaService::processarResposta);
    }

    @RabbitListener(queues = {
            RabbitMQConfig.FILA_CLIENTE_CMD_DLQ,
            RabbitMQConfig.FILA_GERENTE_CMD_DLQ,
            RabbitMQConfig.FILA_CONTA_CMD_DLQ,
            RabbitMQConfig.FILA_AUTH_CMD_DLQ
    })
    public void onDlq(Message message) {
        ler(message, message.getMessageProperties().getConsumerQueue()).ifPresent(sagaService::processarFalhaTecnica);
    }

    // JSON invalido nao melhora com retentativa; o corpo nao e logado porque pode conter a senha
    private Optional<MensagemSaga> ler(Message message, String fila) {
        try {
            return Optional.of(codec.ler(message));
        } catch (JacksonException e) {
            log.warn("[{}] mensagem descartada: JSON invalido", fila);
            return Optional.empty();
        }
    }
}
