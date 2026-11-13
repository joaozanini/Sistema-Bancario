package com.bantads.ms_conta.service;

import com.bantads.ms_conta.model.ContaQuery;
import com.bantads.ms_conta.model.EventoConta;
import com.bantads.ms_conta.repository.ContaQueryRepository;
import com.bantads.ms_conta.repository.EventoContaRepository;
import com.bantads.ms_conta.repository.MovimentacaoQueryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Roda contra o Postgres real: a corrida entre a fila ms.conta.events e a sincronização feita nas consultas
 * só aparece com transações de verdade. Usa uma conta aleatória e apaga tudo o que gravou no fim.
 */
@SpringBootTest(properties = {
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "spring.rabbitmq.listener.direct.auto-startup=false"
})
class ProjecaoConcorrenteTest {

    private static final int DEPOSITOS = 60;
    private static final int CONSULTAS_POR_DEPOSITO = 3;

    @Autowired
    private ContaService service;

    @Autowired
    private EventoContaRepository eventoRepository;

    @Autowired
    private ContaQueryRepository contaQueryRepository;

    @Autowired
    private MovimentacaoQueryRepository movimentacaoQueryRepository;

    private final String numeroConta = "T" + UUID.randomUUID().toString().substring(0, 8);
    private final String cpf = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000_000L, 99_999_999_999L));

    @AfterEach
    void apagarContaDoTeste() {
        movimentacaoQueryRepository.deleteAllInBatch(
                movimentacaoQueryRepository.findByNumeroContaOrderByDataHoraAscVersaoAsc(numeroConta));
        contaQueryRepository.findById(numeroConta).ifPresent(contaQueryRepository::delete);
        eventoRepository.deleteAllInBatch(eventoRepository.findByObjetoIdOrderByVersaoAsc(numeroConta));
    }

    @Test
    @DisplayName("Saldo do lado query fecha com o Event Store quando a fila e as consultas projetam a mesma versão")
    void saldoFechaQuandoFilaEConsultasProjetamAoMesmoTempo() throws Exception {
        EventoConta criado = eventoRepository.saveAndFlush(evento("Criado",
                "{\"cpfCliente\":\"" + cpf + "\",\"gerenteCpf\":\"98574307084\",\"salario\":\"0.00\",\"limite\":\"0.00\"}", 1));
        service.projetarEventoNoLadoQuery(criado);

        ExecutorService executor = Executors.newFixedThreadPool(CONSULTAS_POR_DEPOSITO + 1);
        try {
            for (int i = 0; i < DEPOSITOS; i++) {
                EventoConta deposito = eventoRepository.saveAndFlush(evento("Depósito", "{\"valor\":\"1.00\"}", i + 2));
                CyclicBarrier largada = new CyclicBarrier(CONSULTAS_POR_DEPOSITO + 1);
                List<Future<?>> tarefas = new ArrayList<>();

                // o consumidor da fila projeta o depósito no mesmo instante em que o cliente consulta a conta
                tarefas.add(executor.submit(() -> {
                    largada.await();
                    service.projetarEventoNoLadoQuery(deposito);
                    return null;
                }));
                for (int c = 0; c < CONSULTAS_POR_DEPOSITO; c++) {
                    tarefas.add(executor.submit(() -> {
                        largada.await();
                        return service.buscarContaPorCpf(cpf);
                    }));
                }
                for (Future<?> tarefa : tarefas) {
                    tarefa.get(30, TimeUnit.SECONDS);
                }
            }
        } finally {
            executor.shutdownNow();
        }

        ContaQuery conta = contaQueryRepository.findById(numeroConta).orElseThrow();
        assertEquals(0, new BigDecimal(DEPOSITOS).compareTo(conta.getSaldo()),
                "saldo projetado " + conta.getSaldo() + " para " + DEPOSITOS + " depósitos de 1.00");
        assertEquals(DEPOSITOS + 1, conta.getUltimaVersao());
        assertEquals(DEPOSITOS,
                movimentacaoQueryRepository.findByNumeroContaOrderByDataHoraAscVersaoAsc(numeroConta).size());
    }

    private EventoConta evento(String tipo, String payload, int versao) {
        EventoConta evento = new EventoConta();
        evento.setId(UUID.randomUUID().toString());
        evento.setObjetoId(numeroConta);
        evento.setTipo(tipo);
        evento.setPayload(payload);
        evento.setVersao(versao);
        evento.setTimestamp(LocalDateTime.now());
        return evento;
    }
}
