package com.bantads.ms_orquestrador.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bantads.ms_orquestrador.model.EstadoSaga;
import com.bantads.ms_orquestrador.model.Job;
import com.bantads.ms_orquestrador.model.StatusSaga;
import com.bantads.ms_orquestrador.model.TimeoutPasso;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SagaRepositoryTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final ZSetOperations<String, String> zset = mock(ZSetOperations.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private SagaRepository repository;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valores);
        when(redis.opsForZSet()).thenReturn(zset);
        repository = new SagaRepository(redis, jsonMapper);
    }

    @Test
    void salvaEstadoNoFormatoDoEnunciadoComTtlDeUmaHora() {
        repository.salvar(new EstadoSaga("abc-123", "aprovar-cliente", 3, StatusSaga.EM_ANDAMENTO,
                Map.of("cpf", "12912861012"), "2026-04-30T10:00:00"));

        ArgumentCaptor<String> valor = ArgumentCaptor.forClass(String.class);
        verify(valores).set(eq("saga:abc-123"), valor.capture(), eq(Duration.ofHours(1)));
        JsonNode json = jsonMapper.readTree(valor.getValue());
        assertThat(json.propertyNames())
                .containsExactly("sagaId", "tipo", "etapaAtual", "status", "payload", "timestamp");
        assertThat(json.get("etapaAtual").asInt()).isEqualTo(3);
        assertThat(json.get("status").asString()).isEqualTo("EM_ANDAMENTO");
        assertThat(json.get("payload").get("cpf").asString()).isEqualTo("12912861012");
    }

    @Test
    void leEstadoGravado() {
        when(valores.get("saga:abc-123")).thenReturn("""
                {"sagaId":"abc-123","tipo":"aprovar-cliente","etapaAtual":2,"status":"COMPENSANDO",\
                "payload":{"cpf":"12912861012"},"timestamp":"2026-04-30T10:00:00"}""");

        EstadoSaga estado = repository.buscar("abc-123").orElseThrow();

        assertThat(estado.etapaAtual()).isEqualTo(2);
        assertThat(estado.status()).isEqualTo(StatusSaga.COMPENSANDO);
        assertThat(estado.payload()).containsEntry("cpf", "12912861012");
    }

    @Test
    void salvaJobNaChaveDoJobComTtlDeCincoMinutos() {
        repository.salvarJob(Job.concluidoComRecurso("abc-123", "clientes", "12912861012"));

        ArgumentCaptor<String> valor = ArgumentCaptor.forClass(String.class);
        verify(valores).set(eq("job:abc-123"), valor.capture(), eq(Duration.ofMinutes(5)));
        JsonNode json = jsonMapper.readTree(valor.getValue());
        assertThat(json.propertyNames())
                .containsExactly("jobId", "status", "resultType", "dominio", "resourceId", "erro");
        assertThat(json.get("status").asString()).isEqualTo("CONCLUIDO");
        assertThat(json.get("resultType").asString()).isEqualTo("resource");
        assertThat(json.get("erro").isNull()).isTrue();
    }

    @Test
    void reivindicaEtapaComSetNx() {
        when(valores.setIfAbsent("saga:abc-123:etapa:4", "1", Duration.ofHours(1))).thenReturn(true, false);

        assertThat(repository.reivindicarEtapa("abc-123", 4)).isTrue();
        assertThat(repository.reivindicarEtapa("abc-123", 4)).isFalse();
    }

    @Test
    void agendaTimeoutComInstanteLimiteComoScore() {
        Instant limite = Instant.parse("2026-04-30T10:00:30Z");

        repository.agendarTimeout("abc-123", 4, limite);

        verify(zset).add("saga:timeouts", "abc-123:4", limite.toEpochMilli());
    }

    @Test
    void listaTimeoutsVencidos() {
        Instant agora = Instant.parse("2026-04-30T10:01:00Z");
        when(zset.rangeByScore("saga:timeouts", 0, agora.toEpochMilli()))
                .thenReturn(new LinkedHashSet<>(List.of("abc-123:4", "def-456:1")));

        assertThat(repository.timeoutsVencidos(agora))
                .containsExactly(new TimeoutPasso("abc-123", 4), new TimeoutPasso("def-456", 1));
    }

    @Test
    void removerTimeoutInformaSeFoiEsteChamadorQueRemoveu() {
        when(zset.remove(anyString(), eq("abc-123:4"))).thenReturn(1L, 0L);

        assertThat(repository.removerTimeout("abc-123", 4)).isTrue();
        assertThat(repository.removerTimeout("abc-123", 4)).isFalse();
    }
}
