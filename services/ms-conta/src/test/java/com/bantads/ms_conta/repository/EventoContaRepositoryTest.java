package com.bantads.ms_conta.repository;

import com.bantads.ms_conta.model.EventoConta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Roda contra o Postgres real (o mesmo do contextLoads), porque o erro de tipo
 * da coluna jsonb só aparece no INSERT de verdade. Cada teste é revertido ao final.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoContaRepositoryTest {

    @Autowired
    private EventoContaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Deve gravar o payload na coluna jsonb e ler de volta")
    void deveGravarPayloadNaColunaJsonb() {
        // objetoId aleatório para não colidir com contas que já existam no banco local
        String objetoId = UUID.randomUUID().toString();

        EventoConta evento = new EventoConta();
        evento.setId(UUID.randomUUID().toString());
        evento.setObjetoId(objetoId);
        evento.setTipo("Depósito");
        evento.setPayload("{\"valor\":\"250.00\"}");
        evento.setVersao(1);
        evento.setTimestamp(LocalDateTime.now());

        repository.saveAndFlush(evento);
        entityManager.clear();

        List<EventoConta> eventos = repository.findByObjetoIdOrderByVersaoAsc(objetoId);
        assertEquals(1, eventos.size());
        assertTrue(eventos.get(0).getPayload().contains("250.00"));
    }
}
