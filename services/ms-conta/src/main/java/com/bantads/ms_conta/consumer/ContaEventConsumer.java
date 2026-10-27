package com.bantads.ms_conta.consumer;

import com.bantads.ms_conta.config.RabbitMQConfig;
import com.bantads.ms_conta.model.EventoConta;
import com.bantads.ms_conta.service.ContaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor CQRS do lado Query: escuta os eventos publicados na fila ms.conta.events
 * e projeta de forma assíncrona e idempotente as tabelas de leitura (conta_query e movimentacao_query).
 */
@Component
public class ContaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ContaEventConsumer.class);

    private final ContaService contaService;

    public ContaEventConsumer(ContaService contaService) {
        this.contaService = contaService;
    }

    @RabbitListener(queues = RabbitMQConfig.FILA_CONTA_EVENTS)
    public void consumirEventoConta(EventoConta evento) {
        if (evento == null || evento.getId() == null) {
            log.warn("Evento de conta inválido ou sem ID recebido na fila CQRS.");
            return;
        }
        log.info("Projetando evento CQRS no lado Query: conta={} tipo={} versao={}",
                evento.getObjetoId(), evento.getTipo(), evento.getVersao());
        contaService.projetarEventoNoLadoQuery(evento);
    }
}
