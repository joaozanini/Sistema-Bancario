package com.bantads.ms_conta.service;

import com.bantads.ms_conta.config.RabbitMQConfig;
import com.bantads.ms_conta.dto.*;
import com.bantads.ms_conta.exception.AcessoNegadoException;
import com.bantads.ms_conta.exception.ContaNaoEncontradaException;
import com.bantads.ms_conta.exception.RegraNegocioException;
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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

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

        MovimentacaoResponseDTO response = service.transferir(contaOrigem, request, cpfOrigem);

        assertNotNull(response);
        assertEquals(contaOrigem, response.getNumeroConta());
        assertEquals("TRANSFERENCIA", response.getTipo());
        assertEquals(contaDestino, response.getDestino().numeroConta());
        assertEquals("350.00", response.getValor());
        // a resposta aponta para a conta, por onde o front reconsulta o saldo, e para o extrato
        assertEquals("http://localhost:8080/contas/1291", response.get_links().get("conta").get("href"));
        assertEquals("http://localhost:8080/contas/1291/extrato", response.get_links().get("extrato").get("href"));

        ArgumentCaptor<EventoConta> captor = ArgumentCaptor.forClass(EventoConta.class);
        verify(repository, times(2)).save(captor.capture());

        List<EventoConta> eventosSalvos = captor.getAllValues();
        assertEquals(2, eventosSalvos.size());

        // Gravados em ordem de número da conta (0950 antes de 1291), para transferências cruzadas não travarem
        EventoConta evtDestino = eventosSalvos.get(0);
        EventoConta evtOrigem = eventosSalvos.get(1);
        assertEquals("Cleuddônio", response.getDestino().nome());
        assertEquals("09506382000", response.getDestino().cpf());

        assertEquals(contaOrigem, evtOrigem.getObjetoId());
        assertEquals("TransferênciaOrigem", evtOrigem.getTipo());
        assertEquals(3, evtOrigem.getVersao());
        assertTrue(evtOrigem.getPayload().contains("\"valor\":\"350.00\""));

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

        // 422 (regra do R6), não 404: a conta da URL existe
        assertThrows(RegraNegocioException.class, () ->
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

        assertThrows(RegraNegocioException.class, () ->
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

        MovimentacaoResponseDTO response = service.transferir(contaOrigem, request, cpfOrigem);

        assertNotNull(response);
        assertEquals(contaOrigem, response.getNumeroConta());
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
        assertEquals(conta, res.getNumeroConta());
        assertNull(res.getDestino());
        assertEquals(List.of("conta", "extrato"), List.copyOf(res.get_links().keySet()));

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
    @DisplayName("Cliente não consulta a conta de outro cliente pelo número")
    void deveNegarContaAlheiaParaCliente() {
        when(contaQueryRepository.findById("1291")).thenReturn(Optional.empty());
        when(repository.findByObjetoIdOrderByVersaoAsc("1291")).thenReturn(List.of(
                criarEvento("1291", "Criado", "{\"cpfCliente\":\"12912861012\",\"gerenteCpf\":\"98574307084\"}", 1)));

        assertThrows(AcessoNegadoException.class,
                () -> service.buscarContaPorNumero("1291", "09506382000", "CLIENTE"));
        ContaDTO doTitular = service.buscarContaPorNumero("1291", "12912861012", "CLIENTE");
        assertEquals("1291", doTitular.getNumero());
        assertTrue(doTitular.get_links().containsKey("deposito"));

        // o gerente consulta, mas não movimenta: os links das operações não vão para ele
        ContaDTO doGerente = service.buscarContaPorNumero("1291", "98574307084", "GERENTE");
        assertEquals("1291", doGerente.getNumero());
        assertEquals(List.of("self", "cliente", "extrato"), List.copyOf(doGerente.get_links().keySet()));

        assertEquals("1291", service.buscarContaPorNumero("1291", null, null).getNumero());
    }

    @Test
    @DisplayName("Conta do cliente pelo CPF: o titular e o gerente consultam, outro cliente recebe 403 (R3)")
    void deveBuscarContaDoClientePeloCpf() {
        when(contaQueryRepository.findByCpfCliente("12912861012"))
                .thenReturn(Optional.of(contaProjetada("1291", "12912861012", "800.00", 1)));
        when(contaQueryRepository.findById("1291"))
                .thenReturn(Optional.of(contaProjetada("1291", "12912861012", "800.00", 1)));
        when(repository.findByObjetoIdOrderByVersaoAsc("1291")).thenReturn(List.of());

        ContaDTO doTitular = service.buscarContaDoCliente("12912861012", "12912861012", "CLIENTE");
        assertEquals("1291", doTitular.getNumero());
        assertEquals("800.00", doTitular.getSaldo());
        assertTrue(doTitular.get_links().containsKey("deposito"));

        ContaDTO doGerente = service.buscarContaDoCliente("12912861012", "98574307084", "GERENTE");
        assertFalse(doGerente.get_links().containsKey("deposito"));

        assertThrows(AcessoNegadoException.class,
                () -> service.buscarContaDoCliente("12912861012", "09506382000", "CLIENTE"));
    }

    @Test
    @DisplayName("Conta do cliente pelo CPF responde 404 quando o cliente não tem conta (R3)")
    void deveFalharQuandoClienteNaoTemConta() {
        when(contaQueryRepository.findByCpfCliente("00000000000")).thenReturn(Optional.empty());
        when(repository.findByTipoIn(anyList())).thenReturn(List.of());

        assertThrows(ContaNaoEncontradaException.class,
                () -> service.buscarContaDoCliente("00000000000", "98574307084", "GERENTE"));
    }

    @Test
    @DisplayName("Deve buscar conta por número e retornar DTO com HATEOAS")
    void deveBuscarContaPorNumero() {
        String numeroConta = "1291";
        List<EventoConta> eventos = List.of(
                criarEventoComData(numeroConta, "Criado",
                        "{\"cpfCliente\":\"12912861012\",\"gerenteCpf\":\"98574307084\",\"saldo\":\"800.00\"}", 1,
                        LocalDateTime.of(2000, 1, 1, 0, 0))
        );

        when(contaQueryRepository.findById(numeroConta)).thenReturn(Optional.empty());
        when(repository.findByObjetoIdOrderByVersaoAsc(numeroConta)).thenReturn(eventos);

        ContaDTO dto = service.buscarContaPorNumero(numeroConta);

        assertNotNull(dto);
        assertEquals(numeroConta, dto.getNumero());
        assertEquals("12912861012", dto.getCpfCliente());
        assertEquals("98574307084", dto.getCpfGerente());
        assertEquals("800.00", dto.getSaldo());
        assertEquals("2000-01-01", dto.getDataCriacao());
        assertNotNull(dto.get_links());
        assertEquals(
                List.of("self", "cliente", "deposito", "saque", "transferencia", "extrato"),
                List.copyOf(dto.get_links().keySet()));
        assertEquals("http://localhost:8080/contas/1291", dto.get_links().get("self").get("href"));
        assertEquals("http://localhost:8080/clientes/12912861012", dto.get_links().get("cliente").get("href"));
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
        movPeriodo2.setContaOrigem(numeroConta);
        movPeriodo2.setCpfOrigem(cpf);
        movPeriodo2.setNomeOrigem("Catharyna");
        movPeriodo2.setContaDestino("0950");
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

        ExtratoResponseDTO extrato = service.consultarExtrato(numeroConta, "2026-10-01", "2026-10-31", cpf, "CLIENTE");

        assertNotNull(extrato);
        assertEquals(numeroConta, extrato.getNumeroConta());
        assertEquals("2026-10-01", extrato.getDataInicio());
        assertEquals("2026-10-31", extrato.getDataFim());
        assertEquals("800.00", extrato.getSaldoAbertura());
        assertEquals(2, extrato.getMovimentacoes().size());

        ItemExtratoDTO item1 = extrato.getMovimentacoes().get(0);
        assertEquals("DEPOSITO", item1.getTipo());
        assertEquals("1000.00", item1.getValor());
        assertEquals("2026-10-05T14:30:00", item1.getDataHora());
        // depósito e saque não têm contraparte
        assertNull(item1.getOrigem());
        assertNull(item1.getDestino());

        ItemExtratoDTO item2 = extrato.getMovimentacoes().get(1);
        assertEquals("TRANSFERENCIA", item2.getTipo());
        assertEquals("350.00", item2.getValor());
        assertEquals("Catharyna", item2.getOrigem().nome());
        assertEquals(cpf, item2.getOrigem().cpf());
        assertEquals(numeroConta, item2.getOrigem().numeroConta());
        assertEquals("Cleuddônio", item2.getDestino().nome());
        assertEquals("0950", item2.getDestino().numeroConta());

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
                service.consultarExtrato(numeroConta, "2026-10-01", "2026-10-31", "99999999999", "CLIENTE")
        );
        // sem identidade também não passa
        assertThrows(AcessoNegadoException.class, () ->
                service.consultarExtrato(numeroConta, "2026-10-01", "2026-10-31", null, null)
        );
    }

    @Test
    @DisplayName("Gerente consulta o extrato de qualquer cliente (R7)")
    void devePermitirExtratoParaGerente() {
        String numeroConta = "1291";
        when(repository.findByObjetoIdOrderByVersaoAsc(numeroConta)).thenReturn(List.of());
        when(contaQueryRepository.findById(numeroConta))
                .thenReturn(Optional.of(contaProjetada(numeroConta, "12912861012", "800.00", 1)));

        ExtratoResponseDTO extrato = service.consultarExtrato(numeroConta, "2026-10-01", "2026-10-31", "98574307084", "GERENTE");

        assertEquals("800.00", extrato.getSaldoAbertura());
        assertEquals(List.of("self", "conta"), List.copyOf(extrato.get_links().keySet()));
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
        when(repository.existsById(evtCriado.getId())).thenReturn(true);

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
        simularUpdateDaProjecao(contaSalva);

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
        // o saldo sai pelo UPDATE condicionado à versão anterior, não por save da conta lida
        verify(contaQueryRepository).aplicarVersao(numeroConta, 1, 2, new BigDecimal("500.00"));
        verify(contaQueryRepository, times(1)).save(any());
        assertEquals(new BigDecimal("500.00"), contaSalva.getSaldo());
        assertEquals(2, contaSalva.getUltimaVersao());
    }

    // ------------------------------- casos de borda do Event Store -------------------------------

    private ContaQuery contaProjetada(String numeroConta, String cpf, String saldo, int ultimaVersao) {
        ContaQuery conta = new ContaQuery();
        conta.setNumeroConta(numeroConta);
        conta.setCpfCliente(cpf);
        conta.setGerenteCpf("98574307084");
        conta.setSaldo(new BigDecimal(saldo));
        conta.setDataCriacao(LocalDateTime.of(2026, 10, 1, 9, 0));
        conta.setUltimaVersao(ultimaVersao);
        return conta;
    }

    // Faz o mock responder como o banco: o UPDATE só vale se a conta estiver na versão anterior
    private void simularUpdateDaProjecao(ContaQuery conta) {
        when(contaQueryRepository.aplicarVersao(eq(conta.getNumeroConta()), anyInt(), anyInt(), any()))
                .thenAnswer(chamada -> {
                    int versaoAnterior = chamada.getArgument(1);
                    if (conta.getUltimaVersao() != versaoAnterior) {
                        return 0;
                    }
                    conta.setSaldo(conta.getSaldo().add(chamada.getArgument(3)));
                    conta.setUltimaVersao(chamada.getArgument(2));
                    return 1;
                });
    }

    @Test
    @DisplayName("Deve rejeitar com 422 extrato de mais de 365 dias e aceitar exatamente 365 (R7)")
    void deveRejeitarExtratoComMaisDe365Dias() {
        assertThrows(RegraNegocioException.class, () ->
                service.consultarExtrato("1291", "2020-01-01", "2023-01-01", "12912861012", "CLIENTE"));
        assertThrows(RegraNegocioException.class, () ->
                service.consultarExtrato("1291", "2021-01-01", "2022-01-02", "12912861012", "CLIENTE"));
        // fim anterior ao início também é regra de negócio (422), não dado malformado
        assertThrows(RegraNegocioException.class, () ->
                service.consultarExtrato("1291", "2020-02-01", "2020-01-01", "12912861012", "CLIENTE"));

        when(repository.findByObjetoIdOrderByVersaoAsc("1291")).thenReturn(List.of(
                criarEventoComData("1291", "Criado", "{\"cpfCliente\":\"12912861012\"}", 1, LocalDateTime.of(2019, 1, 1, 0, 0))
        ));
        when(contaQueryRepository.findById("1291")).thenReturn(Optional.of(contaProjetada("1291", "12912861012", "0.00", 1)));

        ExtratoResponseDTO extrato = service.consultarExtrato("1291", "2020-01-01", "2020-12-31", "12912861012", "CLIENTE");
        assertNotNull(extrato);
    }

    @Test
    @DisplayName("Não deve criar uma segunda conta para o mesmo CPF (R1: uma conta por cliente)")
    void naoDeveCriarSegundaContaParaMesmoCpf() {
        when(repository.findByTipoIn(anyList())).thenReturn(List.of(
                criarEvento("1291", "Criado", "{\"cpfCliente\":\"12912861012\",\"gerenteCpf\":\"98574307084\"}", 1)
        ));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.criarConta("12912861012", "98574307084", "5000.00"));

        assertTrue(ex.getMessage().contains("1291"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("R15 nunca devolve as contas ao próprio gerente removido, mesmo que ele venha na lista de ativos")
    void naoDeveTransferirContasParaOProprioGerenteRemovido() {
        String removido = "98574307084";
        String outro = "64065268052";
        // o removido tem 1 conta e o outro tem 2: sem o filtro, o "menos clientes" seria o próprio removido
        when(repository.findByTipoIn(anyList())).thenReturn(List.of(
                criarEvento("1291", "Criado", "{\"cpfCliente\":\"111\",\"gerenteCpf\":\"" + removido + "\"}", 1),
                criarEvento("0950", "Criado", "{\"cpfCliente\":\"222\",\"gerenteCpf\":\"" + outro + "\"}", 1),
                criarEvento("8573", "Criado", "{\"cpfCliente\":\"333\",\"gerenteCpf\":\"" + outro + "\"}", 1)
        ));
        when(repository.findByObjetoIdOrderByVersaoAsc("1291")).thenReturn(List.of(
                criarEvento("1291", "Criado", "{\"cpfCliente\":\"111\",\"gerenteCpf\":\"" + removido + "\"}", 1)
        ));

        Map<String, Object> resultado = service.transferirContas("saga-1", removido, List.of(removido, outro));

        assertEquals(List.of("1291"), resultado.get("contas"));
        ArgumentCaptor<EventoConta> captor = ArgumentCaptor.forClass(EventoConta.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().getPayload().contains("\"gerenteCpf\":\"" + outro + "\""));
    }

    @Test
    @DisplayName("R15 falha quando não há outro gerente ativo para receber as contas")
    void deveFalharTransferenciaDeContasSemOutroGerente() {
        String removido = "98574307084";
        when(repository.findByTipoIn(anyList())).thenReturn(List.of(
                criarEvento("1291", "Criado", "{\"gerenteCpf\":\"" + removido + "\"}", 1)
        ));

        assertThrows(IllegalArgumentException.class, () ->
                service.transferirContas("saga-1", removido, List.of(removido)));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("No empate de clientes, o gerente escolhido é sempre o primeiro da lista (R9)")
    void deveDesempatarGerenteComMenosClientesPelaOrdemDaLista() {
        when(repository.findByTipoIn(anyList())).thenReturn(List.of());

        for (int i = 0; i < 5; i++) {
            assertEquals("33333333333", service.identificarGerenteComMenosClientes(
                    List.of("33333333333", "11111111111", "22222222222")));
        }
    }

    @Test
    @DisplayName("Deve refazer o replay também em deadlock entre transferências cruzadas")
    void deveFazerNovoReplayEmDeadlock() {
        String cpf = "12912861012";
        when(repository.findByObjetoIdOrderByVersaoAsc("1291")).thenReturn(List.of(
                criarEvento("1291", "Criado", "{\"cpfCliente\":\"" + cpf + "\",\"saldo\":\"1000.00\"}", 1)
        ));
        when(repository.findByObjetoIdOrderByVersaoAsc("0950")).thenReturn(List.of(
                criarEvento("0950", "Criado", "{\"cpfCliente\":\"09506382000\"}", 1)
        ));
        doThrow(new CannotAcquireLockException("deadlock detected"))
                .doNothing()
                .when(repository).flush();

        MovimentacaoResponseDTO response = service.transferir("1291", new TransferenciaRequestDTO("0950", "10.00"), cpf);

        assertNotNull(response);
        verify(repository, times(2)).findByObjetoIdOrderByVersaoAsc("1291");
    }

    @Test
    @DisplayName("Dentro de uma transação, o evento só vai para ms.conta.events depois do commit")
    void devePublicarEventoSomenteDepoisDoCommit() {
        when(repository.findByObjetoIdOrderByVersaoAsc("1291")).thenReturn(List.of(
                criarEvento("1291", "Criado", "{\"cpfCliente\":\"12912861012\"}", 1)
        ));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.depositar("1291", new MovimentacaoRequestDTO("50.00"), "12912861012");
            verify(rabbitTemplate, never()).convertAndSend(anyString(), any(Object.class));

            TransactionSynchronizationUtils.invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations());
            verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.FILA_CONTA_EVENTS), any(EventoConta.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Projeção ignora evento reentregue (versão já aplicada)")
    void deveIgnorarEventoJaProjetado() {
        ContaQuery conta = contaProjetada("1291", "12912861012", "500.00", 2);
        when(contaQueryRepository.findById("1291")).thenReturn(Optional.of(conta));

        service.projetarEventoNoLadoQuery(criarEvento("1291", "Depósito", "{\"valor\":\"500.00\"}", 2));

        verify(movimentacaoQueryRepository, never()).save(any());
        verify(contaQueryRepository, never()).save(any());
        verify(contaQueryRepository, never()).aplicarVersao(any(), anyInt(), anyInt(), any());
        assertEquals(new BigDecimal("500.00"), conta.getSaldo());
    }

    @Test
    @DisplayName("Projeção não regrava o saldo quando outra transação já aplicou a mesma versão")
    void naoDeveRegravarSaldoQuandoAVersaoJaFoiAplicadaPorOutraTransacao() {
        // esta transação leu a conta na v1; enquanto isso a outra projetou a v2 (depósito de 150) e confirmou
        ContaQuery contaLidaAntes = contaProjetada("1291", "12912861012", "800.00", 1);
        when(contaQueryRepository.findById("1291")).thenReturn(Optional.of(contaLidaAntes));
        when(contaQueryRepository.aplicarVersao("1291", 1, 2, new BigDecimal("150.00"))).thenReturn(0);

        service.projetarEventoNoLadoQuery(criarEvento("1291", "Depósito", "{\"valor\":\"150.00\"}", 2));

        // nada da conta lida antes volta para o banco: o saldo de 950.00 gravado pela outra transação fica
        verify(contaQueryRepository, never()).save(any());
        verify(movimentacaoQueryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Projeção da troca de gerente só vale para a conta que ainda está na versão anterior (R13/R15)")
    void deveProjetarTrocaDeGerentePeloUpdateCondicional() {
        ContaQuery conta = contaProjetada("1291", "12912861012", "800.00", 4);
        when(contaQueryRepository.findById("1291")).thenReturn(Optional.of(conta));

        service.projetarEventoNoLadoQuery(
                criarEvento("1291", "GerenteAlterado", "{\"gerenteCpf\":\"64065268052\"}", 5));

        verify(contaQueryRepository).aplicarTrocaDeGerente("1291", 4, 5, "64065268052");
        verify(contaQueryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Projeção com lacuna de versão recupera a versão perdida pelo Event Store, em ordem")
    void deveRecuperarLacunaDeVersaoPeloEventStore() {
        ContaQuery conta = contaProjetada("1291", "12912861012", "100.00", 1);
        when(contaQueryRepository.findById("1291")).thenReturn(Optional.of(conta));
        simularUpdateDaProjecao(conta);
        EventoConta v2 = criarEvento("1291", "Depósito", "{\"valor\":\"50.00\"}", 2);
        EventoConta v3 = criarEvento("1291", "Saque", "{\"valor\":\"30.00\"}", 3);
        when(repository.findByObjetoIdOrderByVersaoAsc("1291")).thenReturn(List.of(
                criarEvento("1291", "Criado", "{\"cpfCliente\":\"12912861012\"}", 1), v2, v3
        ));

        // a v2 se perdeu na fila; chega a v3
        service.projetarEventoNoLadoQuery(v3);

        ArgumentCaptor<MovimentacaoQuery> captor = ArgumentCaptor.forClass(MovimentacaoQuery.class);
        verify(movimentacaoQueryRepository, times(2)).save(captor.capture());
        assertEquals("DEPOSITO", captor.getAllValues().get(0).getTipo());
        assertEquals("SAQUE", captor.getAllValues().get(1).getTipo());
        assertEquals(new BigDecimal("120.00"), conta.getSaldo());
        assertEquals(3, conta.getUltimaVersao());
    }

    @Test
    @DisplayName("Projeção ignora o Criado de uma conta já compensada (removida do Event Store)")
    void deveIgnorarCriadoDeContaCompensada() {
        EventoConta criado = criarEvento("4321", "Criado", "{\"cpfCliente\":\"11122233344\"}", 1);
        when(contaQueryRepository.findById("4321")).thenReturn(Optional.empty());
        when(repository.existsById(criado.getId())).thenReturn(false);

        service.projetarEventoNoLadoQuery(criado);

        verify(contaQueryRepository, never()).save(any());
    }
}
