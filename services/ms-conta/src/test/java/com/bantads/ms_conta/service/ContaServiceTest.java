package com.bantads.ms_conta.service;

import com.bantads.ms_conta.dto.ContaDTO;
import com.bantads.ms_conta.dto.TransferenciaRequestDTO;
import com.bantads.ms_conta.dto.TransferenciaResponseDTO;
import com.bantads.ms_conta.exception.AcessoNegadoException;
import com.bantads.ms_conta.exception.ContaNaoEncontradaException;
import com.bantads.ms_conta.exception.SaldoInsuficienteException;
import com.bantads.ms_conta.exception.TransferenciaInvalidaException;
import com.bantads.ms_conta.model.EventoConta;
import com.bantads.ms_conta.repository.EventoContaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContaServiceTest {

    @Mock
    private EventoContaRepository repository;

    private JsonMapper jsonMapper;
    private ContaService service;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        service = new ContaService(repository, jsonMapper);
    }

    private EventoConta criarEvento(String objetoId, String tipo, String payload, int versao) {
        EventoConta evento = new EventoConta();
        evento.setId("evt-" + versao + "-" + objetoId);
        evento.setObjetoId(objetoId);
        evento.setTipo(tipo);
        evento.setPayload(payload);
        evento.setVersao(versao);
        evento.setTimestamp(LocalDateTime.now());
        return evento;
    }

    @Test
    @DisplayName("Deve calcular saldo por replay dinâmico corretamente")
    void deveCalcularSaldoPorReplay() {
        List<EventoConta> eventos = List.of(
                criarEvento("1291", "Criado", "{\"cpfCliente\":\"12912861012\",\"saldo\":\"1000.00\"}", 1),
                criarEvento("1291", "Depósito", "{\"valor\":\"500.00\"}", 2),
                criarEvento("1291", "Saque", "{\"valor\":\"200.00\"}", 3),
                criarEvento("1291", "TransferênciaOrigem", "{\"valor\":\"300.00\"}", 4),
                criarEvento("1291", "TransferênciaDestino", "{\"valor\":\"150.00\"}", 5)
        );

        ContaService.EstadoConta estado = service.reconstruirEstadoPorReplay(eventos);

        // 1000 + 500 - 200 - 300 + 150 = 1150
        assertEquals(new BigDecimal("1150.00"), estado.getSaldo());
        assertEquals("12912861012", estado.getCpfCliente());
    }

    @Test
    @DisplayName("Deve realizar transferência atômica com sucesso gerando 2 eventos e validando saldo por replay")
    void deveRealizarTransferenciaComSucesso() {
        String contaOrigem = "1291";
        String contaDestino = "0950";
        String cpfOrigem = "12912861012";

        List<EventoConta> eventosOrigem = new ArrayList<>(List.of(
                criarEvento(contaOrigem, "Criado", "{\"cpfCliente\":\"" + cpfOrigem + "\",\"saldo\":\"800.00\"}", 1),
                criarEvento(contaOrigem, "Depósito", "{\"valor\":\"200.00\"}", 2)
        )); // Saldo total = 1000.00

        List<EventoConta> eventosDestino = new ArrayList<>(List.of(
                criarEvento(contaDestino, "Criado", "{\"cpfCliente\":\"09506382000\",\"saldo\":\"500.00\"}", 1)
        ));

        when(repository.findByObjetoIdOrderByVersaoAsc(contaOrigem)).thenReturn(eventosOrigem);
        when(repository.findByObjetoIdOrderByVersaoAsc(contaDestino)).thenReturn(eventosDestino);

        TransferenciaRequestDTO request = new TransferenciaRequestDTO();
        request.setContaDestino(contaDestino);
        request.setValor("350.00");
        request.setNomeOrigem("Catharyna");
        request.setNomeDestino("Cleuddônio");

        TransferenciaResponseDTO response = service.transferir(contaOrigem, request, cpfOrigem);

        assertNotNull(response);
        assertEquals(contaOrigem, response.getContaOrigem());
        assertEquals(contaDestino, response.getContaDestino());
        assertEquals("350.00", response.getValor());
        assertNotNull(response.get_links());
        assertTrue(response.get_links().containsKey("self"));
        assertTrue(response.get_links().containsKey("extrato"));

        ArgumentCaptor<EventoConta> captor = ArgumentCaptor.forClass(EventoConta.class);
        verify(repository, times(2)).save(captor.capture());

        List<EventoConta> eventosSalvos = captor.getAllValues();
        assertEquals(2, eventosSalvos.size());

        // Evento Origem
        EventoConta evtOrigem = eventosSalvos.get(0);
        assertEquals(contaOrigem, evtOrigem.getObjetoId());
        assertEquals("TransferênciaOrigem", evtOrigem.getTipo());
        assertEquals(3, evtOrigem.getVersao()); // 2 + 1
        assertTrue(evtOrigem.getPayload().contains("\"valor\":\"350.00\""));
        assertTrue(evtOrigem.getPayload().contains("\"contaDestino\":\"0950\""));

        // Evento Destino
        EventoConta evtDestino = eventosSalvos.get(1);
        assertEquals(contaDestino, evtDestino.getObjetoId());
        assertEquals("TransferênciaDestino", evtDestino.getTipo());
        assertEquals(2, evtDestino.getVersao()); // 1 + 1
        assertTrue(evtDestino.getPayload().contains("\"valor\":\"350.00\""));
        assertTrue(evtDestino.getPayload().contains("\"contaOrigem\":\"1291\""));
    }

    @Test
    @DisplayName("Deve rejeitar transferência quando saldo for insuficiente pelo replay")
    void deveRejeitarQuandoSaldoInsuficiente() {
        String contaOrigem = "1291";
        String contaDestino = "0950";

        List<EventoConta> eventosOrigem = List.of(
                criarEvento(contaOrigem, "Criado", "{\"cpfCliente\":\"12912861012\",\"saldo\":\"100.00\"}", 1)
        );

        when(repository.findByObjetoIdOrderByVersaoAsc(contaOrigem)).thenReturn(eventosOrigem);

        TransferenciaRequestDTO request = new TransferenciaRequestDTO();
        request.setContaDestino(contaDestino);
        request.setValor("150.00");

        assertThrows(SaldoInsuficienteException.class, () ->
                service.transferir(contaOrigem, request, "12912861012")
        );

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar transferência quando usuário autenticado não for o titular")
    void deveRejeitarQuandoUsuarioNaoForTitular() {
        String contaOrigem = "1291";
        List<EventoConta> eventosOrigem = List.of(
                criarEvento(contaOrigem, "Criado", "{\"cpfCliente\":\"12912861012\",\"saldo\":\"1000.00\"}", 1)
        );

        when(repository.findByObjetoIdOrderByVersaoAsc(contaOrigem)).thenReturn(eventosOrigem);

        TransferenciaRequestDTO request = new TransferenciaRequestDTO();
        request.setContaDestino("0950");
        request.setValor("100.00");

        assertThrows(AcessoNegadoException.class, () ->
                service.transferir(contaOrigem, request, "99999999999")
        );

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar transferência quando conta de destino não existir")
    void deveRejeitarQuandoContaDestinoNaoExistir() {
        String contaOrigem = "1291";
        String contaDestino = "9999";

        List<EventoConta> eventosOrigem = List.of(
                criarEvento(contaOrigem, "Criado", "{\"cpfCliente\":\"12912861012\",\"saldo\":\"1000.00\"}", 1)
        );

        when(repository.findByObjetoIdOrderByVersaoAsc(contaOrigem)).thenReturn(eventosOrigem);
        when(repository.findByObjetoIdOrderByVersaoAsc(contaDestino)).thenReturn(List.of());

        TransferenciaRequestDTO request = new TransferenciaRequestDTO();
        request.setContaDestino(contaDestino);
        request.setValor("100.00");

        assertThrows(ContaNaoEncontradaException.class, () ->
                service.transferir(contaOrigem, request, "12912861012")
        );

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar transferência quando conta destino for igual a conta origem")
    void deveRejeitarQuandoContasIguais() {
        TransferenciaRequestDTO request = new TransferenciaRequestDTO();
        request.setContaDestino("1291");
        request.setValor("100.00");

        assertThrows(TransferenciaInvalidaException.class, () ->
                service.transferir("1291", request, "12912861012")
        );

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve buscar conta por número e retornar DTO com HATEOAS")
    void deveBuscarContaPorNumero() {
        String numeroConta = "1291";
        List<EventoConta> eventos = List.of(
                criarEvento(numeroConta, "Criado", "{\"cpfCliente\":\"12912861012\",\"gerenteCpf\":\"98574307084\",\"saldo\":\"800.00\"}", 1)
        );

        when(repository.findByObjetoIdOrderByVersaoAsc(numeroConta)).thenReturn(eventos);

        ContaDTO dto = service.buscarContaPorNumero(numeroConta);

        assertNotNull(dto);
        assertEquals(numeroConta, dto.getNumeroConta());
        assertEquals("12912861012", dto.getCpfCliente());
        assertEquals("98574307084", dto.getGerenteCpf());
        assertNotNull(dto.get_links());
        assertTrue(dto.get_links().containsKey("self"));
    }
}
