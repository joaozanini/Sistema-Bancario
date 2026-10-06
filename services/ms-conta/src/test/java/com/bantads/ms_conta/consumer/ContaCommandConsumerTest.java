package com.bantads.ms_conta.consumer;

import com.bantads.ms_conta.config.RabbitMQConfig;
import com.bantads.ms_conta.dto.MensagemSagaDTO;
import com.bantads.ms_conta.dto.RespostaSagaDTO;
import com.bantads.ms_conta.model.SagaIdempotencia;
import com.bantads.ms_conta.repository.SagaIdempotenciaRepository;
import com.bantads.ms_conta.service.ContaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContaCommandConsumerTest {

    @Mock
    private ContaService contaService;

    @Mock
    private SagaIdempotenciaRepository idempotenciaRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private ContaCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new ContaCommandConsumer(contaService, idempotenciaRepository, rabbitTemplate, objectMapper);
    }

    @Test
    @DisplayName("Deve processar comando de identificar gerente com sucesso e enviar resposta ao orquestrador")
    void deveProcessarIdentificarGerenteComSucesso() {
        MensagemSagaDTO mensagem = new MensagemSagaDTO();
        mensagem.setSagaId("saga-123");
        mensagem.setTipo("conta.identificar-gerente");
        mensagem.setPayload(Map.of("gerentes", List.of("11111111111", "22222222222")));

        when(idempotenciaRepository.findBySagaIdAndTipo("saga-123", "conta.identificar-gerente"))
                .thenReturn(Optional.empty());
        when(contaService.identificarGerenteComMenosClientes(anyList()))
                .thenReturn("22222222222");

        consumer.processarComandoSaga(mensagem);

        ArgumentCaptor<RespostaSagaDTO> captor = ArgumentCaptor.forClass(RespostaSagaDTO.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.FILA_ORQUESTRADOR_REPLY), captor.capture());

        RespostaSagaDTO resposta = captor.getValue();
        assertEquals("saga-123", resposta.getSagaId());
        assertEquals("conta.identificar-gerente", resposta.getTipo());
        assertEquals("SUCESSO", resposta.getStatus());
        assertEquals("22222222222", resposta.getPayload().get("cpfGerente"));

        verify(idempotenciaRepository, times(1)).save(any(SagaIdempotencia.class));
    }

    @Test
    @DisplayName("Deve processar comando de criar conta com sucesso e responder ao orquestrador")
    void deveProcessarCriarContaComSucesso() {
        MensagemSagaDTO mensagem = new MensagemSagaDTO();
        mensagem.setSagaId("saga-456");
        mensagem.setTipo("conta.criar");
        mensagem.setPayload(Map.of(
                "cpfCliente", "12912861012",
                "gerenteCpf", "98574307084",
                "salario", "5000.00"
        ));

        when(idempotenciaRepository.findBySagaIdAndTipo("saga-456", "conta.criar"))
                .thenReturn(Optional.empty());
        when(contaService.criarConta("12912861012", "98574307084", "5000.00"))
                .thenReturn(Map.of("numeroConta", "4567", "cpfCliente", "12912861012", "gerenteCpf", "98574307084"));

        consumer.processarComandoSaga(mensagem);

        ArgumentCaptor<RespostaSagaDTO> captor = ArgumentCaptor.forClass(RespostaSagaDTO.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.FILA_ORQUESTRADOR_REPLY), captor.capture());

        RespostaSagaDTO resposta = captor.getValue();
        assertEquals("saga-456", resposta.getSagaId());
        assertEquals("SUCESSO", resposta.getStatus());
        assertEquals("4567", resposta.getPayload().get("numeroConta"));
    }

    @Test
    @DisplayName("Deve processar comando de compensação de criação de conta com sucesso")
    void deveProcessarCompensacaoCriarConta() {
        MensagemSagaDTO mensagem = new MensagemSagaDTO();
        mensagem.setSagaId("saga-789");
        mensagem.setTipo("conta.compensar-criar");
        mensagem.setPayload(Map.of("numeroConta", "4567", "cpfCliente", "12912861012"));

        when(idempotenciaRepository.findBySagaIdAndTipo("saga-789", "conta.compensar-criar"))
                .thenReturn(Optional.empty());

        consumer.processarComandoSaga(mensagem);

        verify(contaService, times(1)).compensarCriacaoConta("4567", "12912861012");

        ArgumentCaptor<RespostaSagaDTO> captor = ArgumentCaptor.forClass(RespostaSagaDTO.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.FILA_ORQUESTRADOR_REPLY), captor.capture());

        RespostaSagaDTO resposta = captor.getValue();
        assertEquals("SUCESSO", resposta.getStatus());
    }

    @Test
    @DisplayName("Deve garantir idempotência e reenviar resposta anterior sem reexecutar serviço")
    void deveGarantirIdempotencia() throws Exception {
        MensagemSagaDTO mensagem = new MensagemSagaDTO();
        mensagem.setSagaId("saga-123");
        mensagem.setTipo("conta.criar");

        RespostaSagaDTO respostaSalva = RespostaSagaDTO.sucesso("saga-123", "conta.criar", Map.of("numeroConta", "1111"));
        SagaIdempotencia registro = new SagaIdempotencia("id-1", "saga-123", "conta.criar", objectMapper.writeValueAsString(respostaSalva), LocalDateTime.now());

        when(idempotenciaRepository.findBySagaIdAndTipo("saga-123", "conta.criar"))
                .thenReturn(Optional.of(registro));

        consumer.processarComandoSaga(mensagem);

        verify(contaService, never()).criarConta(any(), any(), any());
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.FILA_ORQUESTRADOR_REPLY), any(RespostaSagaDTO.class));
    }
}
