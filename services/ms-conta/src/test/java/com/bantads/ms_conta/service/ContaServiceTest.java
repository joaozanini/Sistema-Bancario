package com.bantads.ms_conta.service;

import com.bantads.ms_conta.config.RabbitMQConfig;
import com.bantads.ms_conta.dto.*;
import com.bantads.ms_conta.exception.AcessoNegadoException;
import com.bantads.ms_conta.exception.ContaNaoEncontradaException;
import com.bantads.ms_conta.exception.SaldoInsuficienteException;
import com.bantads.ms_conta.exception.TransferenciaInvalidaException;
import com.bantads.ms_conta.model.ContaQuery;
import com.bantads.ms_conta.model.EventoConta;
import com.bantads.ms_conta.model.MovimentacaoQuery;
import com.bantads.ms_conta.repository.ContaQueryRepository;
import com.bantads.ms_conta.repository.EventoContaRepository;
import com.bantads.ms_conta.repository.MovimentacaoQueryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContaServiceTest {

    @Mock
    private EventoContaRepository repository;

    @Mock
    private ContaQueryRepository contaQueryRepository;

    @Mock
    private MovimentacaoQueryRepository movimentacaoQueryRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private ContaService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new ContaService(
                repository,
                contaQueryRepository,
                movimentacaoQueryRepository,
                objectMapper,
                rabbitTemplate,
                null
        );
    }

    private EventoConta criarEvento(String objetoId, String tipo, String payload, int versao) {
        return criarEventoComData(objetoId, tipo, payload, versao, LocalDateTime.now());
    }

    private EventoConta criarEventoComData(String objetoId, String tipo, String payload, int versao, LocalDateTime dataHora) {
        EventoConta evento = new EventoConta();
        evento.setId("evt-" + versao + "-" + objetoId);
        evento.setObjetoId(objetoId);
        evento.setTipo(tipo);
        evento.setPayload(payload);
        evento.setVersao(versao);
        evento.setTimestamp(dataHora);
        return evento;
    }

    @Test
    @DisplayName("Deve identificar gerente ativo com menos clientes (R9)")
    void deveIdentificarGerenteComMenosClientes() {
        String ger1 = "98574307084";
        String ger2 = "64065268052";
        String ger3 = "23862179060";

        List<EventoConta> eventos = List.of(
                criarEvento("1291", "Criado", "{\"gerenteCpf\":\"" + ger1 + "\"}", 1),
                criarEvento("0950", "Criado", "{\"gerenteCpf\":\"" + ger1 + "\"}", 1),
                criarEvento("8573", "Criado", "{\"gerenteCpf\":\"" + ger2 + "\"}", 1)
        );

        when(repository.findByTipoIn(anyList())).thenReturn(eventos);

        String escolhido = service.identificarGerenteComMenosClientes(List.of(ger1, ger2, ger3));

        assertEquals(ger3, escolhido);
    }

    @Test
    @DisplayName("Deve identificar conta de menor saldo do gerente com mais contas (R13)")
    void deveIdentificarContaParaNovoGerente() {
        String ger1 = "98574307084";
        String ger2 = "64065268052";

        List<EventoConta> eventosGerentes = List.of(
                criarEvento("1291", "Criado", "{\"gerenteCpf\":\"" + ger1 + "\"}", 1),
                criarEvento("8573", "Criado", "{\"gerenteCpf\":\"" + ger1 + "\"}", 1),
                criarEvento("0950", "Criado", "{\"gerenteCpf\":\"" + ger2 + "\"}", 1)
        );

        when(repository.findByTipoIn(anyList())).thenReturn(eventosGerentes);

        when(repository.findByObjetoIdOrderByVersaoAsc("1291")).thenReturn(List.of(
                criarEvento("1291", "Criado", "{\"cpfCliente\":\"111\",\"gerenteCpf\":\"" + ger1 + "\",\"saldo\":\"800.00\"}", 1)
        ));
        when(repository.findByObjetoIdOrderByVersaoAsc("8573")).thenReturn(List.of(
                criarEvento("8573", "Criado", "{\"cpfCliente\":\"222\",\"gerenteCpf\":\"" + ger1 + "\",\"saldo\":\"200.00\"}", 1)
        ));

        Map<String, Object> resultado = service.identificarContaParaNovoGerente(List.of(ger1, ger2));

        assertNotNull(resultado);
        assertEquals("8573", resultado.get("numeroConta"));
        assertEquals("222", resultado.get("cpfCliente"));
        assertEquals(ger1, resultado.get("gerenteAntigoCpf"));
    }

    @Test
    @DisplayName("Não deve transferir conta se todos os gerentes possuem no máximo 1 conta (R13)")
    void naoDeveTransferirContaSeGerentesComNoMaximoUmaConta() {
        String ger1 = "98574307084";
        String ger2 = "64065268052";

        List<EventoConta> eventosGerentes = List.of(
                criarEvento("1291", "Criado", "{\"gerenteCpf\":\"" + ger1 + "\"}", 1),
                criarEvento("0950", "Criado", "{\"gerenteCpf\":\"" + ger2 + "\"}", 1)
        );

        when(repository.findByTipoIn(anyList())).thenReturn(eventosGerentes);

        Map<String, Object> resultado = service.identificarContaParaNovoGerente(List.of(ger1, ger2));

        assertNull(resultado);
    }

    @Test
    @DisplayName("Deve atribuir conta ao novo gerente gerando evento GerenteAlterado (R13)")
    void deveAtribuirConta() {
        String numeroConta = "1291";
        String novoGerente = "40501740066";
        String antigoGerente = "98574307084";

        when(repository.findByObjetoIdOrderByVersaoAsc(numeroConta)).thenReturn(List.of(
                criarEvento(numeroConta, "Criado", "{\"gerenteCpf\":\"" + antigoGerente + "\"}", 1)
        ));

        Map<String, Object> resultado = service.atribuirConta(numeroConta, novoGerente, antigoGerente);

        assertNotNull(resultado);
        assertEquals("1291", resultado.get("numeroConta"));
        assertEquals(novoGerente, resultado.get("gerenteCpf"));

        ArgumentCaptor<EventoConta> captor = ArgumentCaptor.forClass(EventoConta.class);
        verify(repository).save(captor.capture());

        EventoConta evento = captor.getValue();
        assertEquals(numeroConta, evento.getObjetoId());
        assertEquals("GerenteAlterado", evento.getTipo());
        assertEquals(2, evento.getVersao());
        assertTrue(evento.getPayload().contains(novoGerente));
    }

    @Test
    @DisplayName("Deve criar nova conta com número aleatório de 4 dígitos e persistir evento Criado (R9)")
    void deveCriarContaComSucesso() {
        String cpfCliente = "12912861012";
        String gerenteCpf = "98574307084";
        String salario = "5000.00";

        when(repository.existsByObjetoId(anyString())).thenReturn(false);

        Map<String, Object> resultado = service.criarConta(cpfCliente, gerenteCpf, salario);

        assertNotNull(resultado);
        String numeroConta = (String) resultado.get("numeroConta");
        assertNotNull(numeroConta);
        assertEquals(4, numeroConta.length());
        assertEquals(cpfCliente, resultado.get("cpfCliente"));
        assertEquals(gerenteCpf, resultado.get("gerenteCpf"));

        ArgumentCaptor<EventoConta> captor = ArgumentCaptor.forClass(EventoConta.class);
        verify(repository, times(1)).save(captor.capture());

        EventoConta eventoSalvo = captor.getValue();
        assertEquals(numeroConta, eventoSalvo.getObjetoId());
        assertEquals("Criado", eventoSalvo.getTipo());
        assertEquals(1, eventoSalvo.getVersao());
        assertTrue(eventoSalvo.getPayload().contains(cpfCliente));
        assertTrue(eventoSalvo.getPayload().contains(gerenteCpf));

        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.FILA_CONTA_EVENTS), any(EventoConta.class));
    }

    @Test
    @DisplayName("Deve compensar criação de conta removendo os eventos do Event Store (R9)")
    void deveCompensarCriacaoConta() {
        String numeroConta = "1234";

        service.compensarCriacaoConta(numeroConta, null);

        verify(repository, times(1)).deleteByObjetoId(numeroConta);
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
        ));

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

        EventoConta evtOrigem = eventosSalvos.get(0);
        assertEquals(contaOrigem, evtOrigem.getObjetoId());
        assertEquals("TransferênciaOrigem", evtOrigem.getTipo());
        assertEquals(3, evtOrigem.getVersao());
        assertTrue(evtOrigem.getPayload().contains("\"valor\":\"350.00\""));

        EventoConta evtDestino = eventosSalvos.get(1);
        assertEquals(contaDestino, evtDestino.getObjetoId());
        assertEquals("TransferênciaDestino", evtDestino.getTipo());
        assertEquals(2, evtDestino.getVersao());
        assertTrue(evtDestino.getPayload().contains("\"valor\":\"350.00\""));
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
    @DisplayName("Deve rejeitar transferência quando header de posse (X-User-CPF) não for informado (R6)")
    void deveRejeitarQuandoHeaderPosseNaoInformado() {
        String contaOrigem = "1291";
        List<EventoConta> eventosOrigem = List.of(
                criarEvento(contaOrigem, "Criado", "{\"cpfCliente\":\"12912861012\",\"saldo\":\"1000.00\"}", 1)
        );

        when(repository.findByObjetoIdOrderByVersaoAsc(contaOrigem)).thenReturn(eventosOrigem);

        TransferenciaRequestDTO request = new TransferenciaRequestDTO();
        request.setContaDestino("0950");
        request.setValor("100.00");

        assertThrows(AcessoNegadoException.class, () ->
                service.transferir(contaOrigem, request, null)
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
    @DisplayName("Deve realizar novo replay e tentar novamente em conflito de versão (R6)")
    void deveFazerNovoReplayEmConflitoDeVersao() {
        String contaOrigem = "1291";
        String contaDestino = "0950";
        String cpfOrigem = "12912861012";

        List<EventoConta> eventosOrigemTentativa1 = List.of(
                criarEvento(contaOrigem, "Criado", "{\"cpfCliente\":\"" + cpfOrigem + "\",\"saldo\":\"1000.00\"}", 1)
        );
        List<EventoConta> eventosDestinoTentativa1 = List.of(
                criarEvento(contaDestino, "Criado", "{\"cpfCliente\":\"09506382000\",\"saldo\":\"500.00\"}", 1)
        );

        List<EventoConta> eventosOrigemTentativa2 = List.of(
                criarEvento(contaOrigem, "Criado", "{\"cpfCliente\":\"" + cpfOrigem + "\",\"saldo\":\"1000.00\"}", 1),
                criarEvento(contaOrigem, "Depósito", "{\"valor\":\"100.00\"}", 2)
        );
        List<EventoConta> eventosDestinoTentativa2 = List.of(
                criarEvento(contaDestino, "Criado", "{\"cpfCliente\":\"09506382000\",\"saldo\":\"500.00\"}", 1)
        );

        when(repository.findByObjetoIdOrderByVersaoAsc(contaOrigem))
                .thenReturn(eventosOrigemTentativa1)
                .thenReturn(eventosOrigemTentativa2);
        when(repository.findByObjetoIdOrderByVersaoAsc(contaDestino))
                .thenReturn(eventosDestinoTentativa1)
                .thenReturn(eventosDestinoTentativa2);

        doThrow(new DataIntegrityViolationException("Versão duplicada"))
                .doNothing()
                .when(repository).flush();

        TransferenciaRequestDTO request = new TransferenciaRequestDTO();
        request.setContaDestino(contaDestino);
        request.setValor("200.00");

        TransferenciaResponseDTO response = service.transferir(contaOrigem, request, cpfOrigem);

        assertNotNull(response);
        assertEquals(contaOrigem, response.getContaOrigem());
        verify(repository, times(2)).findByObjetoIdOrderByVersaoAsc(contaOrigem);
    }

    @Test
    @DisplayName("Deve realizar saque com sucesso validando saldo por replay e posse obrigatória (R5)")
    void deveRealizarSaqueComSucesso() {
        String conta = "1291";
        String cpf = "12912861012";
        List<EventoConta> eventos = List.of(
                criarEvento(conta, "Criado", "{\"cpfCliente\":\"" + cpf + "\",\"saldo\":\"1000.00\"}", 1)
        );
        when(repository.findByObjetoIdOrderByVersaoAsc(conta)).thenReturn(eventos);

        MovimentacaoRequestDTO req = new MovimentacaoRequestDTO("300.00");
        MovimentacaoResponseDTO res = service.sacar(conta, req, cpf);

        assertNotNull(res);
        assertEquals("300.00", res.getValor());
        assertEquals("SAQUE", res.getTipo());

        ArgumentCaptor<EventoConta> captor = ArgumentCaptor.forClass(EventoConta.class);
        verify(repository, times(1)).save(captor.capture());
        assertEquals("Saque", captor.getValue().getTipo());
        assertEquals(2, captor.getValue().getVersao());
    }

    @Test
    @DisplayName("Deve rejeitar saque quando saldo for insuficiente pelo replay (R5)")
    void deveRejeitarSaqueSaldoInsuficiente() {
        String conta = "1291";
        String cpf = "12912861012";
        List<EventoConta> eventos = List.of(
                criarEvento(conta, "Criado", "{\"cpfCliente\":\"" + cpf + "\",\"saldo\":\"200.00\"}", 1)
        );
        when(repository.findByObjetoIdOrderByVersaoAsc(conta)).thenReturn(eventos);

        MovimentacaoRequestDTO req = new MovimentacaoRequestDTO("500.00");
        assertThrows(SaldoInsuficienteException.class, () ->
                service.sacar(conta, req, cpf)
        );
    }

    @Test
    @DisplayName("Deve rejeitar saque quando posse obrigatória não for informada (R5)")
    void deveRejeitarSaqueQuandoPosseNaoInformada() {
        String conta = "1291";
        List<EventoConta> eventos = List.of(
                criarEvento(conta, "Criado", "{\"cpfCliente\":\"12912861012\",\"saldo\":\"1000.00\"}", 1)
        );
        when(repository.findByObjetoIdOrderByVersaoAsc(conta)).thenReturn(eventos);

        MovimentacaoRequestDTO req = new MovimentacaoRequestDTO("100.00");
        assertThrows(AcessoNegadoException.class, () ->
                service.sacar(conta, req, null)
        );
    }

    @Test
    @DisplayName("Deve realizar depósito na própria conta com posse obrigatória (R4)")
    void deveRealizarDepositoComSucesso() {
        String conta = "1291";
        String cpf = "12912861012";
        List<EventoConta> eventos = List.of(
                criarEvento(conta, "Criado", "{\"cpfCliente\":\"" + cpf + "\",\"saldo\":\"1000.00\"}", 1)
        );
        when(repository.findByObjetoIdOrderByVersaoAsc(conta)).thenReturn(eventos);

        MovimentacaoResponseDTO res = service.depositar(conta, new MovimentacaoRequestDTO("150.00"), cpf);

        assertNotNull(res);
        assertEquals("150.00", res.getValor());
        assertEquals("DEPOSITO", res.getTipo());

        ArgumentCaptor<EventoConta> captor = ArgumentCaptor.forClass(EventoConta.class);
        verify(repository, times(1)).save(captor.capture());
        assertEquals("Depósito", captor.getValue().getTipo());
        assertEquals(2, captor.getValue().getVersao());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"09506382000", "98574307084"}) // outro cliente e o gerente da conta
    @DisplayName("Deve rejeitar depósito de quem não é o titular, inclusive sem o header X-User-CPF (R4)")
    void deveRejeitarDepositoQuandoUsuarioNaoForTitular(String xUserCpf) {
        String conta = "1291";
        List<EventoConta> eventos = List.of(
                criarEvento(conta, "Criado", "{\"cpfCliente\":\"12912861012\",\"gerenteCpf\":\"98574307084\",\"saldo\":\"1000.00\"}", 1)
        );
        when(repository.findByObjetoIdOrderByVersaoAsc(conta)).thenReturn(eventos);

        assertThrows(AcessoNegadoException.class, () ->
                service.depositar(conta, new MovimentacaoRequestDTO("10.00"), xUserCpf)
        );

        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-10.00", "0.00", "10.005", "10,00", "1E-99999999"})
    @DisplayName("Deve rejeitar valor fora do formato antes de consultar a conta (R4, R5, R6)")
    void deveRejeitarValorForaDoFormato(String valor) {
        assertThrows(IllegalArgumentException.class, () ->
                service.sacar("1291", new MovimentacaoRequestDTO(valor), "12912861012")
        );
        assertThrows(IllegalArgumentException.class, () ->
                service.depositar("1291", new MovimentacaoRequestDTO(valor), "12912861012")
        );
        assertThrows(TransferenciaInvalidaException.class, () ->
                service.transferir("1291", new TransferenciaRequestDTO("0950", valor), "12912861012")
        );

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Deve buscar conta por número e retornar DTO com HATEOAS")
    void deveBuscarContaPorNumero() {
        String numeroConta = "1291";
        List<EventoConta> eventos = List.of(
                criarEvento(numeroConta, "Criado", "{\"cpfCliente\":\"12912861012\",\"gerenteCpf\":\"98574307084\",\"saldo\":\"800.00\"}", 1)
        );

        when(contaQueryRepository.findById(numeroConta)).thenReturn(Optional.empty());
        when(repository.findByObjetoIdOrderByVersaoAsc(numeroConta)).thenReturn(eventos);

        ContaDTO dto = service.buscarContaPorNumero(numeroConta);

        assertNotNull(dto);
        assertEquals(numeroConta, dto.getNumeroConta());
        assertEquals("12912861012", dto.getCpfCliente());
        assertEquals("98574307084", dto.getGerenteCpf());
        assertNotNull(dto.get_links());
        assertTrue(dto.get_links().containsKey("self"));
    }

    @Test
    @DisplayName("Deve consultar extrato no lado query por período calculando saldoAbertura e filtrando movimentações (R7)")
    void deveConsultarExtratoNoLadoQueryComSucesso() {
        String numeroConta = "1291";
        String cpf = "12912861012";

        ContaQuery cq = new ContaQuery();
        cq.setNumeroConta(numeroConta);
        cq.setCpfCliente(cpf);
        cq.setGerenteCpf("98574307084");
        cq.setSaldo(new BigDecimal("1450.00"));
        cq.setUltimaVersao(4);

        MovimentacaoQuery movAnterior = new MovimentacaoQuery();
        movAnterior.setId("m1");
        movAnterior.setNumeroConta(numeroConta);
        movAnterior.setDataHora(LocalDateTime.of(2026, 9, 25, 10, 0));
        movAnterior.setTipo("DEPOSITO");
        movAnterior.setNatureza("ENTRADA");
        movAnterior.setValor(new BigDecimal("800.00"));
        movAnterior.setVersao(2);

        MovimentacaoQuery movPeriodo1 = new MovimentacaoQuery();
        movPeriodo1.setId("m2");
        movPeriodo1.setNumeroConta(numeroConta);
        movPeriodo1.setDataHora(LocalDateTime.of(2026, 10, 5, 14, 30));
        movPeriodo1.setTipo("DEPOSITO");
        movPeriodo1.setNatureza("ENTRADA");
        movPeriodo1.setValor(new BigDecimal("1000.00"));
        movPeriodo1.setVersao(3);

        MovimentacaoQuery movPeriodo2 = new MovimentacaoQuery();
        movPeriodo2.setId("m3");
        movPeriodo2.setNumeroConta(numeroConta);
        movPeriodo2.setDataHora(LocalDateTime.of(2026, 10, 10, 16, 15));
        movPeriodo2.setTipo("TRANSFERENCIA");
        movPeriodo2.setNatureza("SAIDA");
        movPeriodo2.setValor(new BigDecimal("350.00"));
        movPeriodo2.setCpfOrigem(cpf);
        movPeriodo2.setNomeOrigem("Catharyna");
        movPeriodo2.setCpfDestino("09506382000");
        movPeriodo2.setNomeDestino("Cleuddônio");
        movPeriodo2.setVersao(4);

        when(repository.findByObjetoIdOrderByVersaoAsc(numeroConta)).thenReturn(List.of());
        when(contaQueryRepository.findById(numeroConta)).thenReturn(Optional.of(cq));
        when(movimentacaoQueryRepository.findByNumeroContaOrderByDataHoraAscVersaoAsc(numeroConta))
                .thenReturn(List.of(movAnterior, movPeriodo1, movPeriodo2));
        when(movimentacaoQueryRepository.findByNumeroContaAndDataHoraLessThanOrderByDataHoraAscVersaoAsc(
                eq(numeroConta), any(LocalDateTime.class)))
                .thenReturn(List.of(movAnterior));
        when(movimentacaoQueryRepository.findByNumeroContaAndDataHoraBetweenOrderByDataHoraAscVersaoAsc(
                eq(numeroConta), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(movPeriodo1, movPeriodo2));

        ExtratoResponseDTO extrato = service.consultarExtrato(numeroConta, "2026-10-01", "2026-10-31", cpf);

        assertNotNull(extrato);
        assertEquals(numeroConta, extrato.getNumeroConta());
        assertEquals("800.00", extrato.getSaldoAbertura());
        assertEquals("1450.00", extrato.getSaldo());
        assertEquals(2, extrato.getMovimentacoes().size());

        ItemExtratoDTO item1 = extrato.getMovimentacoes().get(0);
        assertEquals("DEPOSITO", item1.getTipo());
        assertEquals("1000.00", item1.getValor());

        ItemExtratoDTO item2 = extrato.getMovimentacoes().get(1);
        assertEquals("TRANSFERENCIA", item2.getTipo());
        assertEquals("350.00", item2.getValor());
        assertEquals("Catharyna", item2.getNomeOrigem());
        assertEquals("Cleuddônio", item2.getNomeDestino());

        assertNotNull(extrato.get_links());
        assertTrue(extrato.get_links().containsKey("self"));
    }

    @Test
    @DisplayName("Deve rejeitar consulta de extrato quando usuário não for o titular da conta (R7)")
    void deveRejeitarConsultaExtratoQuandoUsuarioNaoTitular() {
        String numeroConta = "1291";
        ContaQuery cq = new ContaQuery();
        cq.setNumeroConta(numeroConta);
        cq.setCpfCliente("12912861012");
        cq.setGerenteCpf("98574307084");
        cq.setSaldo(new BigDecimal("800.00"));
        cq.setUltimaVersao(1);

        when(repository.findByObjetoIdOrderByVersaoAsc(numeroConta)).thenReturn(List.of());
        when(contaQueryRepository.findById(numeroConta)).thenReturn(Optional.of(cq));

        assertThrows(AcessoNegadoException.class, () ->
                service.consultarExtrato(numeroConta, "2026-10-01", "2026-10-31", "99999999999")
        );
    }

    @Test
    @DisplayName("Deve projetar eventos CQRS no lado query de forma idempotente (R7)")
    void deveProjetarEventosNoLadoQuery() {
        String numeroConta = "1291";
        String cpf = "12912861012";

        EventoConta evtCriado = criarEventoComData(
                numeroConta,
                "Criado",
                "{\"cpfCliente\":\"" + cpf + "\",\"gerenteCpf\":\"98574307084\",\"saldo\":\"0.00\",\"salario\":\"4000.00\",\"limite\":\"2000.00\"}",
                1,
                LocalDateTime.of(2026, 10, 1, 9, 0)
        );

        when(contaQueryRepository.findById(numeroConta)).thenReturn(Optional.empty());

        service.projetarEventoNoLadoQuery(evtCriado);

        ArgumentCaptor<ContaQuery> contaCaptor = ArgumentCaptor.forClass(ContaQuery.class);
        verify(contaQueryRepository, times(1)).save(contaCaptor.capture());

        ContaQuery contaSalva = contaCaptor.getValue();
        assertEquals(numeroConta, contaSalva.getNumeroConta());
        assertEquals(cpf, contaSalva.getCpfCliente());
        assertEquals(new BigDecimal("0.00"), contaSalva.getSaldo());
        assertEquals(1, contaSalva.getUltimaVersao());

        when(contaQueryRepository.findById(numeroConta)).thenReturn(Optional.of(contaSalva));
        when(movimentacaoQueryRepository.existsById("evt-2-1291")).thenReturn(false);

        EventoConta evtDeposito = criarEventoComData(
                numeroConta,
                "Depósito",
                "{\"valor\":\"500.00\",\"numeroConta\":\"1291\"}",
                2,
                LocalDateTime.of(2026, 10, 2, 11, 0)
        );

        service.projetarEventoNoLadoQuery(evtDeposito);

        ArgumentCaptor<MovimentacaoQuery> movCaptor = ArgumentCaptor.forClass(MovimentacaoQuery.class);
        verify(movimentacaoQueryRepository, times(1)).save(movCaptor.capture());
        assertEquals("DEPOSITO", movCaptor.getValue().getTipo());
        assertEquals("ENTRADA", movCaptor.getValue().getNatureza());
        assertEquals(new BigDecimal("500.00"), movCaptor.getValue().getValor());
        assertEquals(new BigDecimal("500.00"), contaSalva.getSaldo());
    }
}
