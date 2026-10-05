package com.bantads.ms_orquestrador.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bantads.ms_orquestrador.dto.MensagemSaga;
import com.bantads.ms_orquestrador.dto.StatusResposta;
import com.bantads.ms_orquestrador.messaging.SagaPublisher;
import com.bantads.ms_orquestrador.model.EstadoSaga;
import com.bantads.ms_orquestrador.model.Job;
import com.bantads.ms_orquestrador.model.StatusJob;
import com.bantads.ms_orquestrador.model.StatusSaga;
import com.bantads.ms_orquestrador.model.TimeoutPasso;
import com.bantads.ms_orquestrador.repository.SagaRepository;
import com.bantads.ms_orquestrador.saga.AprovarClienteSaga;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SagaServiceTest {

    private static final String SAGA_ID = "3f1c2a7e-0000-4000-8000-000000000001";
    private static final String CPF = "12912861012";
    private static final String EMAIL = "cli1@bantads.com.br";
    private static final Instant AGORA = Instant.parse("2026-04-30T10:00:00Z");

    private record Publicada(String fila, MensagemSaga mensagem) {
    }

    private final SagaRepository repository = mock(SagaRepository.class);
    private final SagaPublisher publisher = mock(SagaPublisher.class);

    private final Map<String, EstadoSaga> estados = new HashMap<>();
    private final List<EstadoSaga> salvos = new ArrayList<>();
    private final Set<String> etapasReivindicadas = new HashSet<>();
    private final List<Publicada> publicadas = new ArrayList<>();

    private SagaService service;

    @BeforeEach
    void setUp() {
        doAnswer(inv -> {
            EstadoSaga estado = inv.getArgument(0);
            estados.put(estado.sagaId(), estado);
            salvos.add(estado);
            return null;
        }).when(repository).salvar(any());
        when(repository.buscar(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(estados.get(inv.<String>getArgument(0))));
        when(repository.reivindicarEtapa(anyString(), anyInt()))
                .thenAnswer(inv -> etapasReivindicadas.add(inv.getArgument(0) + ":" + inv.getArgument(1)));
        doAnswer(inv -> publicadas.add(new Publicada(inv.getArgument(0), inv.getArgument(1))))
                .when(publisher).publicar(anyString(), any());

        Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        service = new SagaService(List.of(new AprovarClienteSaga()), repository, publisher, clock,
                Duration.ofSeconds(30));
    }

    @Test
    void iniciarSalvaEstadoEEnviaPrimeiroPasso() {
        iniciar();

        EstadoSaga estado = estados.get(SAGA_ID);
        assertThat(estado.tipo()).isEqualTo("aprovar-cliente");
        assertThat(estado.etapaAtual()).isEqualTo(1);
        assertThat(estado.status()).isEqualTo(StatusSaga.EM_ANDAMENTO);
        assertThat(estado.payload()).containsExactlyEntriesOf(Map.of("cpf", CPF));
        assertThat(estado.timestamp()).isEqualTo("2026-04-30T10:00:00");

        assertThat(publicadas).singleElement().satisfies(p -> {
            assertThat(p.fila()).isEqualTo("ms.cliente.cmd");
            assertThat(p.mensagem().sagaId()).isEqualTo(SAGA_ID);
            assertThat(p.mensagem().tipo()).isEqualTo("cliente.aprovar-solicitacao");
            assertThat(p.mensagem().payload()).containsExactlyEntriesOf(Map.of("cpf", CPF));
            assertThat(p.mensagem().status()).isNull();
            assertThat(p.mensagem().erro()).isNull();
        });
        verify(repository).agendarTimeout(SAGA_ID, 1, AGORA.plusSeconds(30));
    }

    @Test
    void inicioReentregueEhIgnorado() {
        iniciar();
        iniciar();

        assertThat(publicadas).hasSize(1);
    }

    @Test
    void sagaDeTipoDesconhecidoEhIgnorada() {
        service.iniciar(MensagemSaga.comando(SAGA_ID, "inserir-gerente", null, Map.of()));

        assertThat(publicadas).isEmpty();
        verify(repository, never()).salvar(any());
    }

    @Test
    void respostaDeSucessoIncorporaPayloadEAvanca() {
        iniciar();
        responderPassos(1);

        EstadoSaga estado = estados.get(SAGA_ID);
        assertThat(estado.etapaAtual()).isEqualTo(2);
        assertThat(estado.payload()).containsEntry("nome", "Catharyna").containsEntry("email", EMAIL);
        assertThat(ultima().fila()).isEqualTo("ms.gerente.cmd");
        assertThat(ultima().mensagem().tipo()).isEqualTo("gerente.listar-ativos");
        assertThat(ultima().mensagem().payload()).isEmpty();
        verify(repository).removerTimeout(SAGA_ID, 1);
        verify(repository).agendarTimeout(SAGA_ID, 2, AGORA.plusSeconds(30));
    }

    @Test
    void fluxoCompletoEnviaSenhaPorEmailEConcluiJob() {
        iniciar();
        responderPassos(6);

        assertThat(tipos()).containsExactly(
                "cliente.aprovar-solicitacao",
                "gerente.listar-ativos",
                "conta.escolher-gerente",
                "cliente.criar-cliente",
                "conta.criar-conta",
                "auth.criar-usuario",
                "email.enviar-senha");
        assertThat(mensagem("conta.escolher-gerente").payload())
                .containsEntry("gerentes", List.of("98574307084", "64065268052"));
        assertThat(mensagem("conta.criar-conta").payload())
                .containsExactlyInAnyOrderEntriesOf(Map.of("cpf", CPF, "cpfGerente", "98574307084"));
        assertThat(mensagem("auth.criar-usuario").payload())
                .containsExactlyInAnyOrderEntriesOf(Map.of("cpf", CPF, "email", EMAIL, "tipo", "CLIENTE"));

        assertThat(ultima().fila()).isEqualTo("ms.email.cmd");
        assertThat(ultima().mensagem().payload()).containsExactlyInAnyOrderEntriesOf(
                Map.of("email", EMAIL, "nome", "Catharyna", "senha", "Xy7-senha-gerada"));

        EstadoSaga estado = estados.get(SAGA_ID);
        assertThat(estado.status()).isEqualTo(StatusSaga.CONCLUIDA);
        assertThat(estado.etapaAtual()).isEqualTo(7);
        verify(repository).salvarJob(Job.concluidoComRecurso(SAGA_ID, "clientes", CPF));
        verify(repository, never()).agendarTimeout(eq(SAGA_ID), eq(7), any());
    }

    @Test
    void senhaNuncaEntraNoEstadoSalvo() {
        iniciar();
        responderPassos(6);

        assertThat(salvos).isNotEmpty()
                .allSatisfy(estado -> assertThat(estado.payload()).doesNotContainKey("senha"));
        assertThat(salvos).allSatisfy(estado -> assertThat(estado.payload().values())
                .doesNotContain("Xy7-senha-gerada"));
    }

    @Test
    void falhaCompensaEmOrdemInversaEAvisaCliente() {
        iniciar();
        responderPassos(4);
        publicadas.clear();

        falhar("conta.criar-conta", "Erro ao gravar conta");

        assertThat(tipos()).containsExactly(
                "cliente.criar-cliente.compensar",
                "cliente.aprovar-solicitacao.compensar",
                "email.solicitacao-nao-efetuada");
        assertThat(mensagem("cliente.aprovar-solicitacao.compensar").payload())
                .containsExactlyInAnyOrderEntriesOf(Map.of("cpf", CPF, "statusSolicitacao", "PENDENTE"));
        assertThat(mensagem("email.solicitacao-nao-efetuada").payload())
                .containsExactlyInAnyOrderEntriesOf(Map.of("email", EMAIL, "nome", "Catharyna"));

        assertThat(salvos).extracting(EstadoSaga::status).containsSubsequence(
                StatusSaga.COMPENSANDO, StatusSaga.FALHA);
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.FALHA);
        verify(repository).salvarJob(Job.falha(SAGA_ID, "clientes", "Erro ao gravar conta"));
    }

    @Test
    void loginDuplicadoMarcaSolicitacaoComoNaoAprovada() {
        iniciar();
        responderPassos(5);
        publicadas.clear();

        falhar("auth.criar-usuario", AprovarClienteSaga.ERRO_LOGIN_DUPLICADO);

        assertThat(tipos()).containsExactly(
                "conta.criar-conta.compensar",
                "cliente.criar-cliente.compensar",
                "cliente.aprovar-solicitacao.compensar",
                "email.solicitacao-nao-efetuada");
        assertThat(mensagem("cliente.aprovar-solicitacao.compensar").payload())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "cpf", CPF,
                        "statusSolicitacao", "NAO_APROVADA",
                        "motivo", "E-mail já cadastrado"));

        ArgumentCaptor<Job> job = ArgumentCaptor.forClass(Job.class);
        verify(repository).salvarJob(job.capture());
        assertThat(job.getValue().status()).isEqualTo(StatusJob.FALHA);
        assertThat(job.getValue().erro()).isEqualTo("E-mail já cadastrado");
    }

    @Test
    void timeoutCompensaTambemOPassoQueNaoRespondeu() {
        iniciar();
        responderPassos(4);
        publicadas.clear();

        service.processarTimeout(new TimeoutPasso(SAGA_ID, 5));

        assertThat(tipos()).containsExactly(
                "conta.criar-conta.compensar",
                "cliente.criar-cliente.compensar",
                "cliente.aprovar-solicitacao.compensar",
                "email.solicitacao-nao-efetuada");
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.FALHA);
    }

    @Test
    void dlqSeguidaDeTimeoutCompensaUmaVezSo() {
        iniciar();
        responderPassos(3);
        publicadas.clear();

        service.processarFalhaTecnica(comando("cliente.criar-cliente"));
        service.processarTimeout(new TimeoutPasso(SAGA_ID, 4));

        // a DLQ indica que o MS nao concluiu o passo, entao so os anteriores sao compensados
        assertThat(tipos()).containsExactly(
                "cliente.aprovar-solicitacao.compensar",
                "email.solicitacao-nao-efetuada");
        verify(repository).salvarJob(any());
    }

    @Test
    void falhaDuplicadaLidaAntesDeQualquerGravacaoCompensaUmaVezSo() {
        iniciar();
        responderPassos(3);
        publicadas.clear();
        // DLQ e timeout leem o mesmo estado antes de um dos dois gravar
        EstadoSaga lidoPorAmbos = estados.get(SAGA_ID);
        when(repository.buscar(SAGA_ID)).thenReturn(Optional.of(lidoPorAmbos));

        service.processarFalhaTecnica(comando("cliente.criar-cliente"));
        service.processarTimeout(new TimeoutPasso(SAGA_ID, 4));

        assertThat(tipos()).containsExactly(
                "cliente.aprovar-solicitacao.compensar",
                "email.solicitacao-nao-efetuada");
    }

    @Test
    void respostaReentregueEhIgnorada() {
        iniciar();
        responderPassos(1);
        int enviadas = publicadas.size();

        sucesso("cliente.aprovar-solicitacao", Map.of("cpf", CPF, "nome", "Outro", "email", "x@x.com"));

        assertThat(publicadas).hasSize(enviadas);
        assertThat(estados.get(SAGA_ID).etapaAtual()).isEqualTo(2);
        assertThat(estados.get(SAGA_ID).payload()).containsEntry("nome", "Catharyna");
    }

    @Test
    void respostaDeOutraEtapaEhIgnorada() {
        iniciar();

        sucesso("conta.criar-conta", Map.of("numeroConta", "1291"));

        assertThat(publicadas).hasSize(1);
        assertThat(estados.get(SAGA_ID).etapaAtual()).isEqualTo(1);
    }

    @Test
    void respostaAposFalhaEhIgnorada() {
        iniciar();
        responderPassos(2);
        falhar("conta.escolher-gerente", "Nenhum gerente ativo");
        int enviadas = publicadas.size();

        sucesso("conta.escolher-gerente", Map.of("cpfGerente", "98574307084"));

        assertThat(publicadas).hasSize(enviadas);
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.FALHA);
    }

    @Test
    void falhaNoPrimeiroPassoNaoEnviaEmail() {
        iniciar();
        publicadas.clear();

        falhar("cliente.aprovar-solicitacao", "Solicitacao nao encontrada");

        assertThat(publicadas).isEmpty();
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.FALHA);
        verify(repository).salvarJob(Job.falha(SAGA_ID, "clientes", "Solicitacao nao encontrada"));
    }

    @Test
    void timeoutNoPrimeiroPassoDevolveSolicitacaoParaPendenteSemEmail() {
        iniciar();
        publicadas.clear();

        service.processarTimeout(new TimeoutPasso(SAGA_ID, 1));

        assertThat(tipos()).containsExactly("cliente.aprovar-solicitacao.compensar");
    }

    @Test
    void compensacaoNaDlqSoEhRegistrada() {
        iniciar();
        responderPassos(4);
        falhar("conta.criar-conta", "Erro ao gravar conta");
        int enviadas = publicadas.size();
        int gravacoes = salvos.size();

        service.processarFalhaTecnica(comando("cliente.criar-cliente.compensar"));

        assertThat(publicadas).hasSize(enviadas);
        assertThat(salvos).hasSize(gravacoes);
    }

    private void iniciar() {
        service.iniciar(MensagemSaga.comando(SAGA_ID, "aprovar-cliente", "2026-04-30T10:00:00", Map.of("cpf", CPF)));
    }

    private void responderPassos(int quantidade) {
        List<Runnable> respostas = List.of(
                () -> sucesso("cliente.aprovar-solicitacao", Map.of("cpf", CPF, "nome", "Catharyna", "email", EMAIL)),
                () -> sucesso("gerente.listar-ativos", Map.of("gerentes", List.of("98574307084", "64065268052"))),
                () -> sucesso("conta.escolher-gerente", Map.of("cpfGerente", "98574307084")),
                () -> sucesso("cliente.criar-cliente", Map.of()),
                () -> sucesso("conta.criar-conta", Map.of("numeroConta", "4821")),
                () -> sucesso("auth.criar-usuario", Map.of("senha", "Xy7-senha-gerada")));
        respostas.subList(0, quantidade).forEach(Runnable::run);
    }

    private void sucesso(String tipo, Map<String, Object> payload) {
        service.processarResposta(new MensagemSaga(SAGA_ID, tipo, "2026-04-30T10:00:01", payload,
                StatusResposta.SUCESSO, null));
    }

    private void falhar(String tipo, String erro) {
        service.processarResposta(new MensagemSaga(SAGA_ID, tipo, "2026-04-30T10:00:01", Map.of(),
                StatusResposta.FALHA, erro));
    }

    private static MensagemSaga comando(String tipo) {
        return MensagemSaga.comando(SAGA_ID, tipo, "2026-04-30T10:00:00", Map.of("cpf", CPF));
    }

    private List<String> tipos() {
        return publicadas.stream().map(p -> p.mensagem().tipo()).toList();
    }

    private MensagemSaga mensagem(String tipo) {
        return publicadas.stream().map(Publicada::mensagem).filter(m -> m.tipo().equals(tipo)).findFirst()
                .orElseThrow();
    }

    private Publicada ultima() {
        return publicadas.getLast();
    }
}
