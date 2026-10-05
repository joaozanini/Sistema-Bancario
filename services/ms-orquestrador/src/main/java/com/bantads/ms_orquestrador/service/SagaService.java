package com.bantads.ms_orquestrador.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bantads.ms_orquestrador.dto.MensagemSaga;
import com.bantads.ms_orquestrador.dto.StatusResposta;
import com.bantads.ms_orquestrador.messaging.SagaPublisher;
import com.bantads.ms_orquestrador.model.EstadoSaga;
import com.bantads.ms_orquestrador.model.Job;
import com.bantads.ms_orquestrador.model.StatusSaga;
import com.bantads.ms_orquestrador.model.TimeoutPasso;
import com.bantads.ms_orquestrador.repository.SagaRepository;
import com.bantads.ms_orquestrador.saga.DefinicaoSaga;
import com.bantads.ms_orquestrador.saga.PassoSaga;

/**
 * Motor das SAGAs. Nunca loga payload: a resposta do MS Auth e o comando de
 * e-mail da R9 carregam a senha em claro.
 */
@Service
public class SagaService {

    private static final Logger log = LoggerFactory.getLogger(SagaService.class);

    private static final DateTimeFormatter FORMATO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // a etapa 0 so serve para deduplicar o inicio reentregue
    private static final int ETAPA_INICIO = 0;

    private final Map<String, DefinicaoSaga> definicoes;
    private final SagaRepository repository;
    private final SagaPublisher publisher;
    private final Clock clock;
    private final Duration timeoutPasso;

    public SagaService(List<DefinicaoSaga> definicoes, SagaRepository repository, SagaPublisher publisher,
            Clock clock, @Value("${saga.timeout-passo}") Duration timeoutPasso) {
        this.definicoes = definicoes.stream()
                .collect(Collectors.toMap(DefinicaoSaga::tipo, Function.identity()));
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
        this.timeoutPasso = timeoutPasso;
    }

    public void iniciar(MensagemSaga mensagem) {
        DefinicaoSaga definicao = definicoes.get(mensagem.tipo());
        if (mensagem.sagaId() == null || definicao == null) {
            log.warn("SAGA desconhecida ignorada: sagaId={} tipo={}", mensagem.sagaId(), mensagem.tipo());
            return;
        }
        if (!repository.reivindicarEtapa(mensagem.sagaId(), ETAPA_INICIO)) {
            log.debug("Inicio repetido ignorado: sagaId={}", mensagem.sagaId());
            return;
        }

        Map<String, Object> dados = mensagem.payload() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(mensagem.payload());
        EstadoSaga estado = new EstadoSaga(mensagem.sagaId(), definicao.tipo(), ETAPA_INICIO,
                StatusSaga.EM_ANDAMENTO, dados, agora());
        log.info("SAGA iniciada: sagaId={} tipo={}", estado.sagaId(), estado.tipo());
        executarAPartirDe(estado, definicao, 0, Map.of());
    }

    public void processarResposta(MensagemSaga resposta) {
        Optional<EstadoSaga> encontrado = emAndamento(resposta.sagaId());
        if (encontrado.isEmpty()) {
            log.debug("Resposta sem SAGA em andamento: sagaId={} tipo={}", resposta.sagaId(), resposta.tipo());
            return;
        }
        EstadoSaga estado = encontrado.get();
        DefinicaoSaga definicao = definicoes.get(estado.tipo());
        if (!passoAtual(definicao, estado).tipoComando().equals(resposta.tipo())) {
            log.debug("Resposta fora da etapa atual: sagaId={} tipo={} etapa={}",
                    estado.sagaId(), resposta.tipo(), estado.etapaAtual());
            return;
        }
        if (!resolverPasso(estado)) {
            return;
        }

        log.info("Resposta recebida: sagaId={} tipo={} etapa={} status={}",
                estado.sagaId(), resposta.tipo(), estado.etapaAtual(), resposta.status());
        if (resposta.status() == StatusResposta.SUCESSO) {
            avancar(estado, definicao, resposta.payload());
        } else {
            compensar(estado, definicao, resposta.erro(), false);
        }
    }

    /**
     * Comando que esgotou as retentativas no MS de destino e caiu na DLQ
     */
    public void processarFalhaTecnica(MensagemSaga comando) {
        Optional<EstadoSaga> encontrado = comando.sagaId() == null
                ? Optional.empty()
                : repository.buscar(comando.sagaId());
        if (encontrado.isEmpty()) {
            log.debug("DLQ sem SAGA: sagaId={} tipo={}", comando.sagaId(), comando.tipo());
            return;
        }
        EstadoSaga estado = encontrado.get();
        DefinicaoSaga definicao = definicoes.get(estado.tipo());
        if (ehCompensacao(definicao, comando.tipo())) {
            log.error("Compensacao nao executada (DLQ): sagaId={} tipo={}", comando.sagaId(), comando.tipo());
            return;
        }
        if (estado.status() != StatusSaga.EM_ANDAMENTO
                || !passoAtual(definicao, estado).tipoComando().equals(comando.tipo())) {
            log.debug("DLQ fora da etapa atual: sagaId={} tipo={}", comando.sagaId(), comando.tipo());
            return;
        }
        if (!resolverPasso(estado)) {
            return;
        }

        log.warn("Falha tecnica: sagaId={} tipo={} etapa={}", estado.sagaId(), comando.tipo(), estado.etapaAtual());
        compensar(estado, definicao, "Falha técnica no passo " + comando.tipo(), false);
    }

    public void processarTimeout(TimeoutPasso timeout) {
        Optional<EstadoSaga> encontrado = emAndamento(timeout.sagaId());
        if (encontrado.isEmpty() || encontrado.get().etapaAtual() != timeout.etapa()) {
            log.debug("Timeout de etapa ja resolvida: sagaId={} etapa={}", timeout.sagaId(), timeout.etapa());
            return;
        }
        EstadoSaga estado = encontrado.get();
        if (!resolverPasso(estado)) {
            return;
        }

        DefinicaoSaga definicao = definicoes.get(estado.tipo());
        String tipo = passoAtual(definicao, estado).tipoComando();
        log.warn("Timeout: sagaId={} tipo={} etapa={}", estado.sagaId(), tipo, estado.etapaAtual());
        // sem resposta nao se sabe se o passo executou; as compensacoes sao idempotentes
        compensar(estado, definicao, "Tempo esgotado aguardando " + tipo, true);
    }

    private boolean resolverPasso(EstadoSaga estado) {
        if (!repository.reivindicarEtapa(estado.sagaId(), estado.etapaAtual())) {
            log.debug("Etapa ja resolvida: sagaId={} etapa={}", estado.sagaId(), estado.etapaAtual());
            return false;
        }
        repository.removerTimeout(estado.sagaId(), estado.etapaAtual());
        return true;
    }

    private void avancar(EstadoSaga estado, DefinicaoSaga definicao, Map<String, Object> payloadResposta) {
        Map<String, Object> dados = new LinkedHashMap<>(estado.payload());
        Map<String, Object> transitorios = new HashMap<>();
        if (payloadResposta != null) {
            payloadResposta.forEach((chave, valor) ->
                    (definicao.chavesTransitorias().contains(chave) ? transitorios : dados).put(chave, valor));
        }
        executarAPartirDe(estado.comEtapa(estado.etapaAtual(), dados, agora()), definicao,
                estado.etapaAtual(), transitorios);
    }

    /**
     * Envia os passos fire-and-forget em sequencia ate o proximo passo que
     * aguarda resposta, ou conclui a SAGA.
     */
    private void executarAPartirDe(EstadoSaga estado, DefinicaoSaga definicao, int indice,
            Map<String, Object> transitorios) {
        List<PassoSaga> passos = definicao.passos();
        Map<String, Object> dadosComando = new LinkedHashMap<>(estado.payload());
        dadosComando.putAll(transitorios);

        List<Integer> semResposta = new ArrayList<>();
        int proximo = indice;
        while (proximo < passos.size() && passos.get(proximo).fireAndForget()) {
            semResposta.add(proximo++);
        }

        if (proximo == passos.size()) {
            repository.salvar(new EstadoSaga(estado.sagaId(), estado.tipo(), passos.size(),
                    StatusSaga.CONCLUIDA, estado.payload(), agora()));
            repository.salvarJob(Job.concluidoComRecurso(estado.sagaId(), definicao.dominioJob(),
                    definicao.resourceId(estado.payload())));
            log.info("SAGA concluida: sagaId={} tipo={}", estado.sagaId(), estado.tipo());
            enviarSemResposta(estado.sagaId(), passos, semResposta, dadosComando);
            return;
        }

        int etapa = proximo + 1;
        PassoSaga passo = passos.get(proximo);
        repository.salvar(estado.comEtapa(etapa, estado.payload(), agora()));
        repository.agendarTimeout(estado.sagaId(), etapa, clock.instant().plus(timeoutPasso));
        enviarSemResposta(estado.sagaId(), passos, semResposta, dadosComando);
        enviar(estado.sagaId(), etapa, passo.fila(), passo.tipoComando(), passo.payloadComando().apply(dadosComando));
    }

    private void enviarSemResposta(String sagaId, List<PassoSaga> passos, List<Integer> indices,
            Map<String, Object> dados) {
        for (int indice : indices) {
            PassoSaga passo = passos.get(indice);
            enviar(sagaId, indice + 1, passo.fila(), passo.tipoComando(), passo.payloadComando().apply(dados));
        }
    }

    /**
     * Publica as compensações em ordem inversa sem aguardar resposta
     */
    private void compensar(EstadoSaga estado, DefinicaoSaga definicao, String erro, boolean incluirPassoFalho) {
        EstadoSaga compensando = estado.comStatus(StatusSaga.COMPENSANDO, agora());
        repository.salvar(compensando);

        List<PassoSaga> passos = definicao.passos();
        int ultima = incluirPassoFalho ? estado.etapaAtual() : estado.etapaAtual() - 1;
        for (int etapa = ultima; etapa >= 1; etapa--) {
            PassoSaga passo = passos.get(etapa - 1);
            if (passo.temCompensacao()) {
                enviar(estado.sagaId(), etapa, passo.fila(), passo.tipoCompensacao(),
                        passo.payloadCompensacao().apply(estado.payload(), erro));
            }
        }

        definicao.emailFalha(estado.payload()).ifPresentOrElse(
                email -> enviar(estado.sagaId(), estado.etapaAtual(), email.fila(), email.tipo(), email.payload()),
                () -> log.warn("E-mail de falha nao enviado: sagaId={} etapa={}",
                        estado.sagaId(), estado.etapaAtual()));

        repository.salvar(compensando.comStatus(StatusSaga.FALHA, agora()));
        repository.salvarJob(Job.falha(estado.sagaId(), definicao.dominioJob(), definicao.mensagemErroJob(erro)));
        log.info("SAGA compensada: sagaId={} tipo={} etapa={}", estado.sagaId(), estado.tipo(), estado.etapaAtual());
    }

    private void enviar(String sagaId, int etapa, String fila, String tipo, Map<String, Object> payload) {
        publisher.publicar(fila, MensagemSaga.comando(sagaId, tipo, agora(), payload));
        log.info("Comando enviado: sagaId={} tipo={} etapa={}", sagaId, tipo, etapa);
    }

    private Optional<EstadoSaga> emAndamento(String sagaId) {
        if (sagaId == null) {
            return Optional.empty();
        }
        return repository.buscar(sagaId).filter(estado -> estado.status() == StatusSaga.EM_ANDAMENTO);
    }

    private static PassoSaga passoAtual(DefinicaoSaga definicao, EstadoSaga estado) {
        return definicao.passos().get(estado.etapaAtual() - 1);
    }

    private static boolean ehCompensacao(DefinicaoSaga definicao, String tipo) {
        return definicao.passos().stream().anyMatch(passo -> tipo != null && tipo.equals(passo.tipoCompensacao()));
    }

    private String agora() {
        return LocalDateTime.now(clock).format(FORMATO_TIMESTAMP);
    }
}
