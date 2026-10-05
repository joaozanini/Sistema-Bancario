package com.bantads.ms_orquestrador.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.test.context.SpringBootTest;

// Le o application.properties real de producao; so os listeners e o agendador de timeouts ficam desligados,
// senao tentariam conectar num broker e num Redis que nao existem durante o build.
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "saga.timeout.verificar=false"
})
class TopologiaSagaTest {

    @Autowired
    private Declarables filasSaga;

    @Autowired
    private RabbitProperties rabbitProperties;

    private Map<String, Queue> filasPorNome() {
        return filasSaga.getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .collect(Collectors.toMap(Queue::getName, q -> q));
    }

    @Test
    void declaraOrquestradorReplyESagaCmdSemDlq() {
        Map<String, Queue> filas = filasPorNome();

        assertThat(filas).containsKeys("saga.cmd", "orquestrador.reply");
        // o enunciado nao prevê DLQ para essas duas
        assertThat(filas.get("saga.cmd").getArguments()).isEmpty();
        assertThat(filas.get("orquestrador.reply").getArguments()).isEmpty();
    }

    @Test
    void cadaFilaDeComandoApontaParaSuaDlq() {
        Map<String, Queue> filas = filasPorNome();

        for (String cmd : new String[] { "ms.cliente.cmd", "ms.conta.cmd", "ms.gerente.cmd", "ms.auth.cmd" }) {
            assertThat(filas).as("fila %s", cmd).containsKey(cmd);
            assertThat(filas).as("dlq de %s", cmd).containsKey(cmd + ".dlq");

            assertThat(filas.get(cmd).getArguments())
                    .as("roteamento de DLQ de %s", cmd)
                    .containsEntry("x-dead-letter-exchange", "")
                    .containsEntry("x-dead-letter-routing-key", cmd + ".dlq");

            assertThat(filas.get(cmd + ".dlq").getArguments())
                    .as("a DLQ de %s nao pode ter DLQ propria", cmd)
                    .isEmpty();
        }
    }

    @Test
    void todasAsFilasSaoDuraveis() {
        assertThat(filasSaga.getDeclarables())
                .allMatch(d -> ((Queue) d).isDurable(), "toda fila deve sobreviver a restart do broker");
    }

    @Test
    void retryACadaCincoSegundosAntesDaDlq() {
        RabbitProperties.ListenerRetry retry = rabbitProperties.getListener().getSimple().getRetry();

        assertThat(retry.isEnabled()).isTrue();
        // 3 retentativas apos a entrega inicial = 4 entregas, a ultima em t=15s
        assertThat(retry.getMaxRetries()).isEqualTo(3);
        assertThat(retry.getInitialInterval()).hasSeconds(5);
        assertThat(retry.getMaxInterval()).hasSeconds(5);
        assertThat(retry.getMultiplier()).isEqualTo(1.0);
    }

    @Test
    void mensagemRejeitadaNaoVoltaParaFila() {
        // sem isso a mensagem seria reenfileirada em loop e nunca chegaria na DLQ
        assertThat(rabbitProperties.getListener().getSimple().getDefaultRequeueRejected()).isFalse();
    }

    @Test
    void naoSobraNenhumDeclarableQueNaoSejaFila() {
        assertThat(filasSaga.getDeclarables()).allSatisfy(d -> assertThat(d).isInstanceOf(Queue.class));
    }

    @Test
    void contagemDeFilasBateComOEnunciado() {
        // saga.cmd, orquestrador.reply, ms.email.cmd + 4 cmd + 4 dlq
        assertThat(filasPorNome()).hasSize(11);
    }
}
