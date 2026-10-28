package com.bantads.ms_auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bantads.ms_auth.dto.MensagemSaga;
import com.bantads.ms_auth.dto.StatusResposta;
import com.bantads.ms_auth.exception.CpfJaCadastradoException;
import com.bantads.ms_auth.exception.LoginJaCadastradoException;
import com.bantads.ms_auth.model.ComandoProcessado;
import com.bantads.ms_auth.repository.ComandoProcessadoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ComandoSagaServiceTest {

    private static final String SAGA_ID = "3f1c2a7e-0000-4000-8000-000000000001";
    private static final String CPF = "11122233344";
    private static final String EMAIL = "novo@bantads.com.br";
    private static final String SENHA = "Ab3dEf7h";

    private final UsuarioService usuarioService = mock(UsuarioService.class);
    private final ComandoProcessadoRepository processados = mock(ComandoProcessadoRepository.class);

    private ComandoSagaService service;

    @BeforeEach
    void setUp() {
        when(processados.findBySagaIdAndTipo(anyString(), anyString())).thenReturn(Optional.empty());
        Clock clock = Clock.fixed(Instant.parse("2026-04-30T10:00:00Z"), ZoneOffset.UTC);
        service = new ComandoSagaService(usuarioService, processados, clock);
    }

    @Test
    void criarUsuarioRespondeSucessoComASenha() {
        when(usuarioService.criarCliente(SAGA_ID, CPF, EMAIL)).thenReturn(SENHA);

        MensagemSaga resposta = service.processar(criarUsuario()).orElseThrow();

        assertThat(resposta.status()).isEqualTo(StatusResposta.SUCESSO);
        assertThat(resposta.sagaId()).isEqualTo(SAGA_ID);
        assertThat(resposta.tipo()).isEqualTo(ComandoSagaService.CRIAR_USUARIO);
        assertThat(resposta.timestamp()).isEqualTo("2026-04-30T10:00:00");
        assertThat(resposta.payload()).containsExactly(Map.entry("senha", SENHA));
    }

    @Test
    void registroDeDeduplicacaoNaoGuardaASenha() {
        when(usuarioService.criarCliente(SAGA_ID, CPF, EMAIL)).thenReturn(SENHA);

        service.processar(criarUsuario());

        ArgumentCaptor<ComandoProcessado> captor = ArgumentCaptor.forClass(ComandoProcessado.class);
        verify(processados).save(captor.capture());
        ComandoProcessado registro = captor.getValue();
        assertThat(registro.getSagaId()).isEqualTo(SAGA_ID);
        assertThat(registro.getTipo()).isEqualTo(ComandoSagaService.CRIAR_USUARIO);
        assertThat(registro.getStatus()).isEqualTo(StatusResposta.SUCESSO);
        assertThat(registro.getErro()).isNull();
    }

    @Test
    void loginDuplicadoRespondeComOErroCombinadoComOOrquestrador() {
        when(usuarioService.criarCliente(SAGA_ID, CPF, EMAIL)).thenThrow(new LoginJaCadastradoException(EMAIL));

        MensagemSaga resposta = service.processar(criarUsuario()).orElseThrow();

        assertThat(resposta.status()).isEqualTo(StatusResposta.FALHA);
        assertThat(resposta.erro()).isEqualTo("LOGIN_DUPLICADO");
        assertThat(resposta.payload()).isEmpty();
    }

    @Test
    void cpfDuplicadoRespondeFalhaGenerica() {
        when(usuarioService.criarCliente(SAGA_ID, CPF, EMAIL)).thenThrow(new CpfJaCadastradoException());

        MensagemSaga resposta = service.processar(criarUsuario()).orElseThrow();

        assertThat(resposta.status()).isEqualTo(StatusResposta.FALHA);
        assertThat(resposta.erro()).isNotEqualTo("LOGIN_DUPLICADO");
    }

    @Test
    void sucessoRepetidoNaoGeraNovaRespostaNemNovoUsuario() {
        when(processados.findBySagaIdAndTipo(SAGA_ID, ComandoSagaService.CRIAR_USUARIO)).thenReturn(Optional.of(
                new ComandoProcessado(SAGA_ID, ComandoSagaService.CRIAR_USUARIO, StatusResposta.SUCESSO, null,
                        Instant.now())));

        assertThat(service.processar(criarUsuario())).isEmpty();
        verify(usuarioService, never()).criarCliente(anyString(), anyString(), anyString());
    }

    @Test
    void falhaRepetidaReenviaOMesmoErro() {
        when(processados.findBySagaIdAndTipo(SAGA_ID, ComandoSagaService.CRIAR_USUARIO)).thenReturn(Optional.of(
                new ComandoProcessado(SAGA_ID, ComandoSagaService.CRIAR_USUARIO, StatusResposta.FALHA,
                        "LOGIN_DUPLICADO", Instant.now())));

        MensagemSaga resposta = service.processar(criarUsuario()).orElseThrow();

        assertThat(resposta.status()).isEqualTo(StatusResposta.FALHA);
        assertThat(resposta.erro()).isEqualTo("LOGIN_DUPLICADO");
        verify(usuarioService, never()).criarCliente(anyString(), anyString(), anyString());
    }

    @Test
    void payloadIncompletoRespondeFalha() {
        MensagemSaga comando = new MensagemSaga(SAGA_ID, ComandoSagaService.CRIAR_USUARIO, null,
                Map.of("cpf", CPF), null, null);

        MensagemSaga resposta = service.processar(comando).orElseThrow();

        assertThat(resposta.status()).isEqualTo(StatusResposta.FALHA);
        verify(usuarioService, never()).criarCliente(anyString(), anyString(), anyString());
    }

    @Test
    void compensacaoRemoveERespondeSucesso() {
        MensagemSaga comando = new MensagemSaga(SAGA_ID, ComandoSagaService.COMPENSAR_CRIAR_USUARIO, null,
                Map.of("cpf", CPF), null, null);

        MensagemSaga resposta = service.processar(comando).orElseThrow();

        verify(usuarioService).removerCriadoPelaSaga(SAGA_ID, CPF);
        assertThat(resposta.status()).isEqualTo(StatusResposta.SUCESSO);
        verify(processados, never()).save(any());
    }

    @Test
    void tipoDesconhecidoRespondeFalha() {
        MensagemSaga comando = new MensagemSaga(SAGA_ID, "auth.outro", null, Map.of(), null, null);

        MensagemSaga resposta = service.processar(comando).orElseThrow();

        assertThat(resposta.status()).isEqualTo(StatusResposta.FALHA);
    }

    @Test
    void comandoSemSagaIdEDescartado() {
        MensagemSaga comando = new MensagemSaga(null, ComandoSagaService.CRIAR_USUARIO, null,
                Map.of("cpf", CPF, "email", EMAIL), null, null);

        assertThat(service.processar(comando)).isEmpty();
    }

    @Test
    void criarGerenteUsaASenhaInformadaENaoADevolve() {
        MensagemSaga resposta = service.processar(criarGerente(Map.of(
                "cpf", CPF, "email", EMAIL, "tipo", "GERENTE", "senha", SENHA))).orElseThrow();

        verify(usuarioService).criarGerente(SAGA_ID, CPF, EMAIL, SENHA);
        verify(usuarioService, never()).criarCliente(anyString(), anyString(), anyString());
        assertThat(resposta.status()).isEqualTo(StatusResposta.SUCESSO);
        assertThat(resposta.payload()).isEmpty();
    }

    @Test
    void gerenteSemSenhaRespondeFalha() {
        MensagemSaga resposta = service.processar(criarGerente(Map.of(
                "cpf", CPF, "email", EMAIL, "tipo", "GERENTE"))).orElseThrow();

        assertThat(resposta.status()).isEqualTo(StatusResposta.FALHA);
        verify(usuarioService, never()).criarGerente(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void gerenteComLoginDuplicadoRespondeOErroCombinado() {
        doThrow(new LoginJaCadastradoException(EMAIL))
                .when(usuarioService).criarGerente(SAGA_ID, CPF, EMAIL, SENHA);

        MensagemSaga resposta = service.processar(criarGerente(Map.of(
                "cpf", CPF, "email", EMAIL, "tipo", "GERENTE", "senha", SENHA))).orElseThrow();

        assertThat(resposta.status()).isEqualTo(StatusResposta.FALHA);
        assertThat(resposta.erro()).isEqualTo("LOGIN_DUPLICADO");
    }

    @Test
    void tipoDeUsuarioDesconhecidoRespondeFalha() {
        MensagemSaga resposta = service.processar(criarGerente(Map.of(
                "cpf", CPF, "email", EMAIL, "tipo", "ADMIN", "senha", SENHA))).orElseThrow();

        assertThat(resposta.status()).isEqualTo(StatusResposta.FALHA);
        verify(usuarioService, never()).criarGerente(anyString(), anyString(), anyString(), anyString());
    }

    private static MensagemSaga criarGerente(Map<String, Object> payload) {
        return new MensagemSaga(SAGA_ID, ComandoSagaService.CRIAR_USUARIO, "2026-04-30T09:59:59",
                payload, null, null);
    }

    private static MensagemSaga criarUsuario() {
        return new MensagemSaga(SAGA_ID, ComandoSagaService.CRIAR_USUARIO, "2026-04-30T09:59:59",
                Map.of("cpf", CPF, "email", EMAIL, "tipo", "CLIENTE"), null, null);
    }
}
