package com.bantads.ms_orquestrador.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.bantads.ms_orquestrador.model.StatusSaga;
import com.bantads.ms_orquestrador.model.TimeoutPasso;
import com.bantads.ms_orquestrador.repository.SagaRepository;
import com.bantads.ms_orquestrador.repository.SessaoRepository;
import com.bantads.ms_orquestrador.saga.RemoverGerenteSaga;
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

class RemoverGerenteSagaTest {

    private static final String SAGA_ID = "7c9e6679-0000-4000-8000-000000000015";
    private static final String CPF = "64065268052";
    private static final String OUTRO_GERENTE = "98574307084";
    private static final String CPF_CLIENTE = "09506382000";
    private static final Instant AGORA = Instant.parse("2026-04-30T10:00:00Z");

    private record Publicada(String fila, MensagemSaga mensagem) {
    }

    private final SagaRepository repository = mock(SagaRepository.class);
    private final SagaPublisher publisher = mock(SagaPublisher.class);
    private final SessaoRepository sessoes = mock(SessaoRepository.class);
    private final SegredosSaga segredos = new SegredosSaga();

    private final Map<String, EstadoSaga> estados = new HashMap<>();
    private final Set<String> etapasReivindicadas = new HashSet<>();
    private final List<Publicada> publicadas = new ArrayList<>();

    private SagaService service;

    @BeforeEach
    void setUp() {
        doAnswer(inv -> {
            EstadoSaga estado = inv.getArgument(0);
            estados.put(estado.sagaId(), estado);
            return null;
        }).when(repository).salvar(any());
        when(repository.buscar(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(estados.get(inv.<String>getArgument(0))));
        when(repository.reivindicarEtapa(anyString(), anyInt()))
                .thenAnswer(inv -> etapasReivindicadas.add(inv.getArgument(0) + ":" + inv.getArgument(1)));
        doAnswer(inv -> publicadas.add(new Publicada(inv.getArgument(0), inv.getArgument(1))))
                .when(publisher).publicar(anyString(), any());

        Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        service = new SagaService(List.of(new RemoverGerenteSaga(sessoes)), repository, publisher, segredos, clock,
                Duration.ofSeconds(30));
    }

    @Test
    void iniciaInativandoOGerente() {
        iniciar();

        assertThat(publicadas).singleElement().satisfies(p -> {
            assertThat(p.fila()).isEqualTo("ms.gerente.cmd");
            assertThat(p.mensagem().tipo()).isEqualTo("gerente.inativar");
            assertThat(p.mensagem().payload()).containsExactlyEntriesOf(Map.of("cpf", CPF));
        });
        verify(sessoes, never()).encerrarPorCpf(anyString());
    }

    @Test
    void sessaoSoCaiDepoisDoAuthDesativarEAntesDeListarOsAtivos() {
        iniciar();
        sucesso("gerente.inativar", Map.of());
        verify(sessoes, never()).encerrarPorCpf(anyString());

        sucesso("auth.desativar-usuario", Map.of());

        verify(sessoes).encerrarPorCpf(CPF);
        assertThat(tipos()).containsExactly("gerente.inativar", "auth.desativar-usuario", "gerente.listar-ativos");
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.EM_ANDAMENTO);
    }

    @Test
    void encerraASessaoAntesDeEnviarOProximoComando() {
        List<String> eventos = new ArrayList<>();
        doAnswer(inv -> eventos.add("publicou " + inv.<MensagemSaga>getArgument(1).tipo()))
                .when(publisher).publicar(anyString(), any());
        doAnswer(inv -> eventos.add("encerrou sessao")).when(sessoes).encerrarPorCpf(CPF);

        iniciar();
        sucesso("gerente.inativar", Map.of());
        sucesso("auth.desativar-usuario", Map.of());

        assertThat(eventos).containsExactly("publicou gerente.inativar", "publicou auth.desativar-usuario",
                "encerrou sessao", "publicou gerente.listar-ativos");
    }

    @Test
    void transfereAsContasParaOsAtivosSemIncluirORemovido() {
        iniciar();
        sucesso("gerente.inativar", Map.of());
        sucesso("auth.desativar-usuario", Map.of());
        sucesso("gerente.listar-ativos", Map.of("gerentes", List.of(OUTRO_GERENTE, CPF, "23862179060")));

        assertThat(mensagem("conta.transferir-contas").payload())
                .containsEntry("gerenteRemovidoCpf", CPF)
                .containsEntry("gerentes", List.of(OUTRO_GERENTE, "23862179060"));
    }

    @Test
    void comClientesTransferidosAvisaTodosEConcluiComResultadoInline() {
        percorrerAteAsContasTransferidas();
        sucesso("cliente.obter-dados", Map.of("clientes",
                List.of(Map.of("cpf", CPF_CLIENTE, "nome", "Cleuddônio", "email", "cli2@bantads.com.br"))));

        assertThat(tipos()).containsExactly("gerente.inativar", "auth.desativar-usuario", "gerente.listar-ativos",
                "conta.transferir-contas", "cliente.obter-dados", "email.remocao-gerente");
        assertThat(mensagem("cliente.obter-dados").payload()).containsEntry("cpfs", List.of(CPF_CLIENTE));
        assertThat(publicadas.get(publicadas.size() - 1).fila()).isEqualTo("ms.email.cmd");
        assertThat(mensagem("email.remocao-gerente").payload()).containsKey("clientes");
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.CONCLUIDA);
        verify(repository).salvarJob(Job.concluidoInline(SAGA_ID, "gerentes",
                Map.of("mensagem", RemoverGerenteSaga.MENSAGEM_SUCESSO)));
    }

    @Test
    void semContasATransferirPulaConsultaAoMsClienteEEmailEConclui() {
        iniciar();
        sucesso("gerente.inativar", Map.of());
        sucesso("auth.desativar-usuario", Map.of());
        sucesso("gerente.listar-ativos", Map.of("gerentes", List.of(OUTRO_GERENTE)));
        sucesso("conta.transferir-contas", Map.of("cpfsClientes", List.of()));

        assertThat(tipos()).containsExactly("gerente.inativar", "auth.desativar-usuario", "gerente.listar-ativos",
                "conta.transferir-contas");
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.CONCLUIDA);
        verify(repository).salvarJob(Job.concluidoInline(SAGA_ID, "gerentes",
                Map.of("mensagem", RemoverGerenteSaga.MENSAGEM_SUCESSO)));
    }

    @Test
    void recusaDoUltimoGerenteAtivoTerminaEmFalhaSemApagarSessaoNemCompensar() {
        iniciar();
        falhar("gerente.inativar", "Não é permitido remover o último gerente ativo");

        assertThat(tipos()).containsExactly("gerente.inativar");
        verify(sessoes, never()).encerrarPorCpf(anyString());
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.FALHA);
        verify(repository).salvarJob(Job.falha(SAGA_ID, "gerentes", "Não é permitido remover o último gerente ativo"));
    }

    @Test
    void falhaNoAuthReativaOGerenteESessaoContinua() {
        iniciar();
        sucesso("gerente.inativar", Map.of());
        falhar("auth.desativar-usuario", "Erro no MS Auth");

        assertThat(tipos()).containsExactly("gerente.inativar", "auth.desativar-usuario", "gerente.inativar.compensar");
        assertThat(mensagem("gerente.inativar.compensar").payload()).containsExactlyEntriesOf(Map.of("cpf", CPF));
        verify(sessoes, never()).encerrarPorCpf(anyString());
    }

    @Test
    void falhaNaTransferenciaCompensaAuthEGerenteSemMexerNaSessao() {
        iniciar();
        sucesso("gerente.inativar", Map.of());
        sucesso("auth.desativar-usuario", Map.of());
        sucesso("gerente.listar-ativos", Map.of("gerentes", List.of(OUTRO_GERENTE)));
        falhar("conta.transferir-contas", "Erro ao transferir");

        assertThat(tipos()).endsWith("auth.desativar-usuario.compensar", "gerente.inativar.compensar");
        assertThat(tipos()).doesNotContain("conta.transferir-contas.compensar");
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.FALHA);
        verify(repository).salvarJob(Job.falha(SAGA_ID, "gerentes", "Erro ao transferir"));
    }

    @Test
    void timeoutNaTransferenciaDevolveAsContasAoGerenteOriginal() {
        iniciar();
        sucesso("gerente.inativar", Map.of());
        sucesso("auth.desativar-usuario", Map.of());
        sucesso("gerente.listar-ativos", Map.of("gerentes", List.of(OUTRO_GERENTE)));
        service.processarTimeout(new TimeoutPasso(SAGA_ID, 5));

        assertThat(tipos()).endsWith("conta.transferir-contas.compensar", "auth.desativar-usuario.compensar",
                "gerente.inativar.compensar");
        assertThat(mensagem("conta.transferir-contas.compensar").payload())
                .containsEntry("gerenteOriginalCpf", CPF).doesNotContainKey("cpfsClientes");
    }

    @Test
    void falhaDoMsClienteDepoisDaTransferenciaDevolveAsContasComOsCpfs() {
        percorrerAteAsContasTransferidas();
        falhar("cliente.obter-dados", "MS Cliente indisponível");

        assertThat(tipos()).endsWith("conta.transferir-contas.compensar", "auth.desativar-usuario.compensar",
                "gerente.inativar.compensar");
        assertThat(mensagem("conta.transferir-contas.compensar").payload())
                .containsEntry("gerenteOriginalCpf", CPF).containsEntry("cpfsClientes", List.of(CPF_CLIENTE));
    }

    private void percorrerAteAsContasTransferidas() {
        iniciar();
        sucesso("gerente.inativar", Map.of());
        sucesso("auth.desativar-usuario", Map.of());
        sucesso("gerente.listar-ativos", Map.of("gerentes", List.of(OUTRO_GERENTE)));
        sucesso("conta.transferir-contas", Map.of("cpfsClientes", List.of(CPF_CLIENTE)));
    }

    private void iniciar() {
        service.iniciar(MensagemSaga.comando(SAGA_ID, "remover-gerente", "2026-04-30T10:00:00",
                new HashMap<>(Map.of("cpf", CPF))));
    }

    private void sucesso(String tipo, Map<String, Object> payload) {
        service.processarResposta(new MensagemSaga(SAGA_ID, tipo, "2026-04-30T10:00:01", payload,
                StatusResposta.SUCESSO, null));
    }

    private void falhar(String tipo, String erro) {
        service.processarResposta(new MensagemSaga(SAGA_ID, tipo, "2026-04-30T10:00:01", Map.of(),
                StatusResposta.FALHA, erro));
    }

    private List<String> tipos() {
        return publicadas.stream().map(p -> p.mensagem().tipo()).toList();
    }

    private MensagemSaga mensagem(String tipo) {
        return publicadas.stream().map(Publicada::mensagem).filter(m -> m.tipo().equals(tipo)).findFirst()
                .orElseThrow();
    }
}
