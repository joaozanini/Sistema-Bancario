package com.bantads.ms_orquestrador.service;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.bantads.ms_orquestrador.model.TimeoutPasso;
import com.bantads.ms_orquestrador.repository.SagaRepository;

/**
 * Prazos ficam num sorted set no Redis para sobreviver a restart do
 * orquestrador.
 */
@Component
@ConditionalOnProperty(name = "saga.timeout.verificar", havingValue = "true", matchIfMissing = true)
public class SagaTimeoutScheduler {

    private final SagaRepository repository;
    private final SagaService sagaService;
    private final Clock clock;

    public SagaTimeoutScheduler(SagaRepository repository, SagaService sagaService, Clock clock) {
        this.repository = repository;
        this.sagaService = sagaService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${saga.timeout.intervalo-verificacao}")
    public void verificarVencidos() {
        for (TimeoutPasso timeout : repository.timeoutsVencidos(clock.instant())) {
            if (repository.removerTimeout(timeout.sagaId(), timeout.etapa())) {
                sagaService.processarTimeout(timeout);
            }
        }
    }
}
