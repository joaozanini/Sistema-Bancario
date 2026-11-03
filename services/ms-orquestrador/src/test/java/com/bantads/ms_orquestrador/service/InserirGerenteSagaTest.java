package com.bantads.ms_orquestrador.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
import com.bantads.ms_orquestrador.saga.AprovarClienteSaga;
import com.bantads.ms_orquestrador.saga.InserirGerenteSaga;
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

class InserirGerenteSagaTest {

    private static final String SAGA_ID = "7c9e6679-0000-4000-8000-000000000013";
    private static final String CPF = "11144477735";
    private static final String SENHA = "senhaDoFormulario";
    private static final String CPF_CLIENTE = "58872160006";
    private static final String GERENTE_ORIGINAL = "98574307084";
    private static final Instant AGORA = Instant.parse("2026-04-30T10:00:00Z");

    private record Publicada(String fila, MensagemSaga mensagem) {
    }

    private final SagaRepository repository = mock(SagaRepository.class);
    private final SagaPublisher publisher = mock(SagaPublisher.class);
    private final SegredosSaga segredos = new SegredosSaga();

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
        service = new SagaService(List.of(new AprovarClienteSaga(), new InserirGerenteSaga()), repository,
                publisher, segredos, clock, Duration.ofSeconds(30));
    }

    @Test
    void iniciaInserindoOGerenteSemASenha() {
        iniciar();

        assertThat(estados.get(SAGA_ID).payload()).doesNotContainKey("senha").containsEntry("cpf", CPF);
        assertThat(publicadas).singleElement().satisfies(p -> {
            assertThat(p.fila()).isEqualTo("ms.gerente.cmd");
            assertThat(p.mensagem().tipo()).isEqualTo("gerente.inserir");
            assertThat(p.mensagem().payload()).doesNotContainKey("senha")
                    .containsEntry("nome", "Gerente Novo").containsEntry("telefone", "41999990000");
        });
    }

    @Test
    void senhaChegaSoNoComandoDoMsAuth() {
        iniciar();
        sucesso("gerente.inserir", Map.of());

        MensagemSaga auth = mensagem("auth.criar-usuario");
        assertThat(publicadas.stream().filter(p -> p.fila().equals("ms.auth.cmd"))).hasSize(1);
        assertThat(auth.payload()).containsEntry("senha", SENHA).containsEntry("tipo", "GERENTE")
                .containsEntry("cpf", CPF).containsEntry("email", "novo.gerente@bantads.com.br");
        assertThat(publicadas.stream().filter(p -> !p.mensagem().tipo().equals("auth.criar-usuario")))
                .allSatisfy(p -> assertThat(p.mensagem().payload()).doesNotContainKey("senha"));
        assertThat(salvos).allSatisfy(estado -> assertThat(estado.payload()).doesNotContainKey("senha"));
    }

    @Test
    void comContaATransferirExecutaTodosOsPassos() {
        iniciar();
        sucesso("gerente.inserir", Map.of());
        sucesso("auth.criar-usuario", Map.of());
        sucesso("conta.escolher-conta-transferencia", Map.of("temConta", true, "numeroConta", "5887",
                "cpfCliente", CPF_CLIENTE, "gerenteAntigoCpf", GERENTE_ORIGINAL));
        sucesso("conta.atribuir-gerente", Map.of());
        sucesso("cliente.obter-dados", Map.of("clientes",
                List.of(Map.of("cpf", CPF_CLIENTE, "nome", "Cutardo", "email", "cli4@bantads.com.br"))));

        assertThat(tipos()).containsExactly("gerente.inserir", "auth.criar-usuario",
                "conta.escolher-conta-transferencia", "conta.atribuir-gerente", "cliente.obter-dados",
                "email.troca-gerente");
        assertThat(mensagem("conta.atribuir-gerente").payload()).containsEntry("numeroConta", "5887")
                .containsEntry("novoGerenteCpf", CPF).containsEntry("gerenteAntigoCpf", GERENTE_ORIGINAL);
        assertThat(mensagem("cliente.obter-dados").payload()).containsEntry("cpfs", List.of(CPF_CLIENTE));
        assertThat(mensagem("email.troca-gerente").payload()).containsEntry("nomeGerente", "Gerente Novo")
                .containsKey("clientes");
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.CONCLUIDA);
        verify(repository).salvarJob(Job.concluidoComRecurso(SAGA_ID, "gerentes", CPF));
        assertThat(segredos.buscar(SAGA_ID)).isEmpty();
    }

    @Test
    void semContaATransferirPulaOsPassosCondicionaisEConclui() {
        iniciar();
        sucesso("gerente.inserir", Map.of());
        sucesso("auth.criar-usuario", Map.of());
        sucesso("conta.escolher-conta-transferencia", Map.of("temConta", false));

        assertThat(tipos()).containsExactly("gerente.inserir", "auth.criar-usuario",
                "conta.escolher-conta-transferencia");
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.CONCLUIDA);
        verify(repository).salvarJob(Job.concluidoComRecurso(SAGA_ID, "gerentes", CPF));
        assertThat(segredos.buscar(SAGA_ID)).isEmpty();
    }

    @Test
    void loginDuplicadoRemoveOGerenteETerminaEmFalha() {
        iniciar();
        sucesso("gerente.inserir", Map.of());
        falhar("auth.criar-usuario", "LOGIN_DUPLICADO");

        assertThat(tipos()).containsExactly("gerente.inserir", "auth.criar-usuario", "gerente.inserir.compensar");
        assertThat(mensagem("gerente.inserir.compensar").payload()).containsExactlyEntriesOf(Map.of("cpf", CPF));
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.FALHA);
        verify(repository).salvarJob(Job.falha(SAGA_ID, "gerentes", "E-mail já cadastrado"));
        assertThat(segredos.buscar(SAGA_ID)).isEmpty();
    }

    @Test
    void falhaNaAtribuicaoCompensaEmOrdemInversa() {
        iniciar();
        sucesso("gerente.inserir", Map.of());
        sucesso("auth.criar-usuario", Map.of());
        sucesso("conta.escolher-conta-transferencia", Map.of("temConta", true, "numeroConta", "5887",
                "cpfCliente", CPF_CLIENTE, "gerenteAntigoCpf", GERENTE_ORIGINAL));
        service.processarTimeout(new TimeoutPasso(SAGA_ID, 4));

        assertThat(tipos()).endsWith("conta.atribuir-gerente.compensar", "auth.criar-usuario.compensar",
                "gerente.inserir.compensar");
        assertThat(mensagem("conta.atribuir-gerente.compensar").payload())
                .containsEntry("numeroConta", "5887").containsEntry("gerenteAntigoCpf", GERENTE_ORIGINAL);
        assertThat(estados.get(SAGA_ID).status()).isEqualTo(StatusSaga.FALHA);
    }

    private void iniciar() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("cpf", CPF);
        payload.put("nome", "Gerente Novo");
        payload.put("email", "novo.gerente@bantads.com.br");
        payload.put("telefone", "41999990000");
        payload.put("senha", SENHA);
        service.iniciar(MensagemSaga.comando(SAGA_ID, "inserir-gerente", "2026-04-30T10:00:00", payload));
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
