package com.bantads.ms_gerente.service;

import com.bantads.ms_gerente.dto.GerenteDTO;
import com.bantads.ms_gerente.dto.MensagemSaga;
import com.bantads.ms_gerente.dto.StatusResposta;
import com.bantads.ms_gerente.exception.CpfJaCadastradoException;
import com.bantads.ms_gerente.exception.GerenteNaoEncontradoException;
import com.bantads.ms_gerente.model.Gerente;
import com.bantads.ms_gerente.model.SagaIdempotencia;
import com.bantads.ms_gerente.repository.SagaIdempotenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComandoSagaServiceTest {

    private static final String SAGA = "saga-1";
    private static final String CPF = "98574307084";

    @Mock
    private GerenteService gerenteService;

    @Mock
    private SagaIdempotenciaRepository idempotenciaRepository;

    private final Map<String, SagaIdempotencia> registros = new HashMap<>();

    private ComandoSagaService service;

    @BeforeEach
    void setUp() {
        lenient().when(idempotenciaRepository.findBySagaIdAndTipo(anyString(), anyString()))
                .thenAnswer(chamada -> Optional.ofNullable(
                        registros.get(chamada.getArgument(0) + "|" + chamada.getArgument(1))));
        lenient().when(idempotenciaRepository.save(any(SagaIdempotencia.class))).thenAnswer(chamada -> {
            SagaIdempotencia registro = chamada.getArgument(0);
            registros.put(registro.getSagaId() + "|" + registro.getTipo(), registro);
            return registro;
        });
        Clock clock = Clock.fixed(Instant.parse("2026-04-30T13:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        service = new ComandoSagaService(gerenteService, idempotenciaRepository, JsonMapper.builder().build(), clock);
    }

    @Test
    @DisplayName("Compensação da inserção remove o gerente que a própria SAGA inseriu")
    void deveRemoverGerenteInseridoPelaSaga() {
        when(gerenteService.inserir(any(Gerente.class)))
                .thenReturn(new GerenteDTO(CPF, "Geniéve", "ger1@bantads.com.br", null, true));

        service.processar(comando(ComandoSagaService.INSERIR));
        MensagemSaga resposta = service.processar(comando(ComandoSagaService.INSERIR_COMPENSAR)).orElseThrow();

        assertThat(resposta.status()).isEqualTo(StatusResposta.SUCESSO);
        verify(gerenteService).remover(CPF);
    }

    @Test
    @DisplayName("Compensação da inserção não apaga gerente que já existia (CPF repetido)")
    void naoDeveRemoverGerentePreExistente() {
        when(gerenteService.inserir(any(Gerente.class)))
                .thenThrow(new CpfJaCadastradoException("CPF já cadastrado para outro gerente."));

        service.processar(comando(ComandoSagaService.INSERIR));
        MensagemSaga resposta = service.processar(comando(ComandoSagaService.INSERIR_COMPENSAR)).orElseThrow();

        assertThat(resposta.status()).isEqualTo(StatusResposta.SUCESSO);
        verify(gerenteService, never()).remover(anyString());
    }

    @Test
    @DisplayName("Inserção que chega depois da compensação é recusada e não cria o gerente")
    void naoDeveInserirDepoisDeCompensar() {
        service.processar(comando(ComandoSagaService.INSERIR_COMPENSAR));
        MensagemSaga resposta = service.processar(comando(ComandoSagaService.INSERIR)).orElseThrow();

        assertThat(resposta.status()).isEqualTo(StatusResposta.FALHA);
        verify(gerenteService, never()).inserir(any());
        verify(gerenteService, never()).remover(anyString());
    }

    @Test
    @DisplayName("Compensação da remoção reativa o gerente que a própria SAGA inativou")
    void deveReativarGerenteInativadoPelaSaga() {
        service.processar(comando(ComandoSagaService.INATIVAR));
        service.processar(comando(ComandoSagaService.INATIVAR_COMPENSAR));

        verify(gerenteService).reativar(CPF);
    }

    @Test
    @DisplayName("Compensação da remoção não reativa gerente que a SAGA não inativou")
    void naoDeveReativarGerenteQueASagaNaoInativou() {
        doThrow(new GerenteNaoEncontradoException("Gerente não encontrado.")).when(gerenteService).inativar(CPF);

        MensagemSaga inativacao = service.processar(comando(ComandoSagaService.INATIVAR)).orElseThrow();
        service.processar(comando(ComandoSagaService.INATIVAR_COMPENSAR));

        assertThat(inativacao.status()).isEqualTo(StatusResposta.FALHA);
        verify(gerenteService, never()).reativar(anyString());
    }

    @Test
    @DisplayName("Comando reentregue devolve a resposta guardada sem executar de novo")
    void deveExecutarUmaVezPorSagaETipo() {
        service.processar(comando(ComandoSagaService.INATIVAR));
        service.processar(comando(ComandoSagaService.INATIVAR));

        verify(gerenteService, times(1)).inativar(CPF);
    }

    private MensagemSaga comando(String tipo) {
        return new MensagemSaga(SAGA, tipo, "2026-04-30T10:00:00", Map.of("cpf", CPF), null, null);
    }
}
