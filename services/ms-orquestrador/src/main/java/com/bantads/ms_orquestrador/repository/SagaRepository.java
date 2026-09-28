package com.bantads.ms_orquestrador.repository;

import com.bantads.ms_orquestrador.model.EstadoSaga;
import com.bantads.ms_orquestrador.model.Job;
import com.bantads.ms_orquestrador.model.TimeoutPasso;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Estado das SAGAs, jobs e prazos dos passos no Redis. Valores gravados como
 * JSON legivel para inspecao pelo redis-cli.
 */
@Repository
public class SagaRepository {

    static final Duration TTL_SAGA = Duration.ofHours(1);
    static final Duration TTL_JOB = Duration.ofMinutes(5);
    static final String CHAVE_TIMEOUTS = "saga:timeouts";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public SagaRepository(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    public void salvar(EstadoSaga estado) {
        redis.opsForValue().set(chaveSaga(estado.sagaId()), jsonMapper.writeValueAsString(estado), TTL_SAGA);
    }

    public Optional<EstadoSaga> buscar(String sagaId) {
        return Optional.ofNullable(redis.opsForValue().get(chaveSaga(sagaId)))
                .map(json -> jsonMapper.readValue(json, EstadoSaga.class));
    }

    /**
     * Resposta, DLQ e timeout podem resolver o mesmo passo; so o primeiro a
     * gravar a chave de controle segue adiante.
     */
    public boolean reivindicarEtapa(String sagaId, int etapa) {
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(chaveSaga(sagaId) + ":etapa:" + etapa, "1", TTL_SAGA));
    }

    public void agendarTimeout(String sagaId, int etapa, Instant limite) {
        redis.opsForZSet().add(CHAVE_TIMEOUTS, membro(sagaId, etapa), limite.toEpochMilli());
    }

    public boolean removerTimeout(String sagaId, int etapa) {
        Long removidos = redis.opsForZSet().remove(CHAVE_TIMEOUTS, membro(sagaId, etapa));
        return removidos != null && removidos > 0;
    }

    public List<TimeoutPasso> timeoutsVencidos(Instant agora) {
        Set<String> membros = redis.opsForZSet().rangeByScore(CHAVE_TIMEOUTS, 0, agora.toEpochMilli());
        if (membros == null) {
            return List.of();
        }
        return membros.stream().map(SagaRepository::timeoutPasso).toList();
    }

    public void salvarJob(Job job) {
        redis.opsForValue().set("job:" + job.jobId(), jsonMapper.writeValueAsString(job), TTL_JOB);
    }

    private static String chaveSaga(String sagaId) {
        return "saga:" + sagaId;
    }

    private static String membro(String sagaId, int etapa) {
        return sagaId + ":" + etapa;
    }

    private static TimeoutPasso timeoutPasso(String membro) {
        int separador = membro.lastIndexOf(':');
        return new TimeoutPasso(membro.substring(0, separador), Integer.parseInt(membro.substring(separador + 1)));
    }
}
