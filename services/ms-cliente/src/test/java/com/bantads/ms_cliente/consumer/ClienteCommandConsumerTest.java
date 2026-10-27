package com.bantads.ms_cliente.consumer;

import com.bantads.ms_cliente.config.RabbitMQConfig;
import com.bantads.ms_cliente.dto.MensagemSagaDTO;
import com.bantads.ms_cliente.dto.RespostaSagaDTO;
import com.bantads.ms_cliente.repository.SagaIdempotenciaRepository;
import com.bantads.ms_cliente.service.ClienteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteCommandConsumerTest {

    @Mock
    private ClienteService clienteService;

    @Mock
    private SagaIdempotenciaRepository idempotenciaRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ClienteCommandConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ClienteCommandConsumer(clienteService, idempotenciaRepository, rabbitTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("Deve processar comando de obter dados dos clientes transferidos e responder ao orquestrador (R15)")
    void deveProcessarObterClientesTransferidosComSucesso() {
        String sagaId = "saga-r15-123";
        String tipo = "cliente.obter-clientes-transferidos";

        Map<String, Object> payload = Map.of(
                "clientesTransferidos", List.of(
                        Map.of("cpfCliente", "12912861012", "numeroConta", "1291"),
                        Map.of("cpfCliente", "09506382000", "numeroConta", "0950")
                )
        );

        List<Map<String, Object>> dadosRetorno = List.of(
                Map.of("cpf", "12912861012", "nome", "Catharyna", "email", "cli1@bantads.com.br"),
                Map.of("cpf", "09506382000", "nome", "Cleuddônio", "email", "cli2@bantads.com.br")
        );

        when(idempotenciaRepository.findBySagaIdAndTipo(sagaId, tipo)).thenReturn(Optional.empty());
        when(clienteService.obterDadosClientesTransferidos(List.of("12912861012", "09506382000")))
                .thenReturn(dadosRetorno);

        MensagemSagaDTO mensagem = new MensagemSagaDTO(sagaId, tipo, "2026-10-27T18:00:00", payload);
        consumer.processarComandoSaga(mensagem);

        ArgumentCaptor<RespostaSagaDTO> captor = ArgumentCaptor.forClass(RespostaSagaDTO.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.FILA_ORQUESTRADOR_REPLY), captor.capture());

        RespostaSagaDTO resposta = captor.getValue();
        assertEquals(sagaId, resposta.getSagaId());
        assertEquals(tipo, resposta.getTipo());
        assertEquals("SUCESSO", resposta.getStatus());
        assertNotNull(resposta.getPayload());
        assertEquals(dadosRetorno, resposta.getPayload().get("clientes"));
        assertEquals(dadosRetorno, resposta.getPayload().get("clientesTransferidos"));
    }

    @Test
    @DisplayName("Deve processar comando de obter clientes transferidos a partir de lista simples de CPFs (R15)")
    void deveProcessarObterClientesTransferidosListaSimples() {
        String sagaId = "saga-r15-456";
        String tipo = "cliente.obter-clientes-transferidos";

        Map<String, Object> payload = Map.of("cpfs", List.of("12912861012"));
        List<Map<String, Object>> dadosRetorno = List.of(
                Map.of("cpf", "12912861012", "nome", "Catharyna", "email", "cli1@bantads.com.br")
        );

        when(idempotenciaRepository.findBySagaIdAndTipo(sagaId, tipo)).thenReturn(Optional.empty());
        when(clienteService.obterDadosClientesTransferidos(List.of("12912861012"))).thenReturn(dadosRetorno);

        MensagemSagaDTO mensagem = new MensagemSagaDTO(sagaId, tipo, "2026-10-27T18:00:00", payload);
        consumer.processarComandoSaga(mensagem);

        ArgumentCaptor<RespostaSagaDTO> captor = ArgumentCaptor.forClass(RespostaSagaDTO.class);
        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.FILA_ORQUESTRADOR_REPLY), captor.capture());

        RespostaSagaDTO resposta = captor.getValue();
        assertEquals("SUCESSO", resposta.getStatus());
        assertEquals(dadosRetorno, resposta.getPayload().get("clientes"));
    }
}
