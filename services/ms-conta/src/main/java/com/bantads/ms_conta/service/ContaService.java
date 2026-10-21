package com.bantads.ms_conta.service;

import com.bantads.ms_conta.config.RabbitMQConfig;
import com.bantads.ms_conta.dto.ContaDTO;
import com.bantads.ms_conta.dto.MovimentacaoRequestDTO;
import com.bantads.ms_conta.dto.MovimentacaoResponseDTO;
import com.bantads.ms_conta.dto.TransferenciaRequestDTO;
import com.bantads.ms_conta.dto.TransferenciaResponseDTO;
import com.bantads.ms_conta.exception.AcessoNegadoException;
import com.bantads.ms_conta.exception.ContaNaoEncontradaException;
import com.bantads.ms_conta.exception.SaldoInsuficienteException;
import com.bantads.ms_conta.exception.TransferenciaInvalidaException;
import com.bantads.ms_conta.model.EventoConta;
import com.bantads.ms_conta.repository.EventoContaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class ContaService {

    private static final Logger log = LoggerFactory.getLogger(ContaService.class);
    private static final int MAX_TENTATIVAS_REPLAY = 3;

    // Até 15 dígitos inteiros e 2 casas decimais, o que cabe no NUMERIC(19, 4). Também barra a notação
    // científica: "1E-99999999" passa no new BigDecimal() e prende a thread por mais de 1 min no setScale
    private static final Pattern FORMATO_VALOR = Pattern.compile("\\d{1,15}(\\.\\d{1,2})?");

    private final EventoContaRepository repository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Random random = new Random();

    public ContaService(
            EventoContaRepository repository,
            ObjectMapper objectMapper,
            @Autowired(required = false) RabbitTemplate rabbitTemplate,
            @Autowired(required = false) PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
    }

    private <T> T executarEmTransacao(Supplier<T> acao) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> acao.get());
        }
        return acao.get();
    }

    /**
     * SAGA Aprovar Cliente (R9 - Passo 3): Identifica, dentre os gerentes ativos informados,
     * o que possui menos clientes/contas atreladas (gerentes sem conta possuem 0 clientes).
     */
    @Transactional(readOnly = true)
    public String identificarGerenteComMenosClientes(List<String> gerentesAtivos) {
        if (gerentesAtivos == null || gerentesAtivos.isEmpty()) {
            throw new IllegalArgumentException("Lista de gerentes ativos não pode ser vazia.");
        }

        Map<String, Long> contagemPorGerente = new HashMap<>();
        for (String cpf : gerentesAtivos) {
            if (cpf != null && !cpf.isBlank()) {
                contagemPorGerente.put(cpf.trim(), 0L);
            }
        }

        Map<String, String> gerenteAtualPorConta = mapearGerenteAtualPorConta();

        for (String gerenteCpf : gerenteAtualPorConta.values()) {
            if (contagemPorGerente.containsKey(gerenteCpf)) {
                contagemPorGerente.put(gerenteCpf, contagemPorGerente.get(gerenteCpf) + 1);
            }
        }

        String gerenteEscolhido = null;
        long menorQuantidade = Long.MAX_VALUE;

        for (Map.Entry<String, Long> entry : contagemPorGerente.entrySet()) {
            if (entry.getValue() < menorQuantidade) {
                menorQuantidade = entry.getValue();
                gerenteEscolhido = entry.getKey();
            }
        }

        log.info("Gerente escolhido com menos clientes: {} (total: {})", gerenteEscolhido, menorQuantidade);
        return gerenteEscolhido != null ? gerenteEscolhido : gerentesAtivos.get(0);
    }

    /**
     * SAGA Inserir Gerente (R13 - Passo 3): Identifica qual conta vai passar para o novo gerente.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> identificarContaParaNovoGerente(List<String> gerentesAtivos) {
        if (gerentesAtivos == null || gerentesAtivos.isEmpty()) {
            return null;
        }

        Map<String, String> gerentePorConta = mapearGerenteAtualPorConta();

        Map<String, List<String>> contasPorGerente = new HashMap<>();
        for (String cpf : gerentesAtivos) {
            if (cpf != null && !cpf.isBlank()) {
                contasPorGerente.put(cpf.trim(), new ArrayList<>());
            }
        }

        for (Map.Entry<String, String> entry : gerentePorConta.entrySet()) {
            String conta = entry.getKey();
            String gerente = entry.getValue();
            if (contasPorGerente.containsKey(gerente)) {
                contasPorGerente.get(gerente).add(conta);
            }
        }

        int maxContas = 0;
        for (List<String> contas : contasPorGerente.values()) {
            if (contas.size() > maxContas) {
                maxContas = contas.size();
            }
        }

        if (maxContas <= 1) {
            log.info("Nenhum gerente ativo possui mais de 1 conta. Nenhuma conta será transferida.");
            return null;
        }

        List<String> candidatosMaisContas = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : contasPorGerente.entrySet()) {
            if (entry.getValue().size() == maxContas) {
                candidatosMaisContas.add(entry.getKey());
            }
        }

        String gerenteEscolhido = null;
        if (candidatosMaisContas.size() == 1) {
            gerenteEscolhido = candidatosMaisContas.get(0);
        } else {
            BigDecimal menorSaldoTotal = null;
            for (String gerenteCpf : candidatosMaisContas) {
                BigDecimal saldoTotal = BigDecimal.ZERO;
                for (String numConta : contasPorGerente.get(gerenteCpf)) {
                    List<EventoConta> evts = repository.findByObjetoIdOrderByVersaoAsc(numConta);
                    EstadoConta est = reconstruirEstadoPorReplay(evts);
                    saldoTotal = saldoTotal.add(est.getSaldo());
                }
                if (menorSaldoTotal == null || saldoTotal.compareTo(menorSaldoTotal) < 0) {
                    menorSaldoTotal = saldoTotal;
                    gerenteEscolhido = gerenteCpf;
                }
            }
        }

        List<String> contasDoGerente = contasPorGerente.get(gerenteEscolhido);
        String contaMenorSaldo = null;
        BigDecimal menorSaldoConta = null;

        for (String numConta : contasDoGerente) {
            List<EventoConta> evts = repository.findByObjetoIdOrderByVersaoAsc(numConta);
            EstadoConta est = reconstruirEstadoPorReplay(evts);
            if (menorSaldoConta == null || est.getSaldo().compareTo(menorSaldoConta) < 0) {
                menorSaldoConta = est.getSaldo();
                contaMenorSaldo = numConta;
            }
        }

        List<EventoConta> evtsConta = repository.findByObjetoIdOrderByVersaoAsc(contaMenorSaldo);
        EstadoConta estConta = reconstruirEstadoPorReplay(evtsConta);
        String cpfCliente = estConta.getCpfCliente();

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("numeroConta", contaMenorSaldo);
        resultado.put("cpfCliente", cpfCliente);
        resultado.put("gerenteAntigoCpf", gerenteEscolhido);
        resultado.put("saldo", menorSaldoConta != null ? menorSaldoConta.setScale(2, RoundingMode.HALF_UP).toString() : "0.00");

        log.info("Conta identificada para transferência: {} (cliente: {}, gerente atual: {})",
                contaMenorSaldo, cpfCliente, gerenteEscolhido);
        return resultado;
    }

    /**
     * SAGA Inserir Gerente (R13 - Passo 4): Atribui a conta ao novo gerente, gerando
     * o evento GerenteAlterado no Event Store.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> atribuirConta(String numeroConta, String novoGerenteCpf, String gerenteAntigoCpf) {
        if (numeroConta == null || numeroConta.isBlank()) {
            throw new IllegalArgumentException("Número da conta é obrigatório.");
        }
        if (novoGerenteCpf == null || novoGerenteCpf.isBlank()) {
            throw new IllegalArgumentException("CPF do novo gerente é obrigatório.");
        }

        List<EventoConta> eventos = repository.findByObjetoIdOrderByVersaoAsc(numeroConta.trim());
        if (eventos.isEmpty()) {
            throw new ContaNaoEncontradaException("Conta não encontrada: " + numeroConta);
        }

        int proximaVersao = eventos.get(eventos.size() - 1).getVersao() + 1;
        LocalDateTime agora = LocalDateTime.now();

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("gerenteCpf", novoGerenteCpf.trim());
        payloadMap.put("gerenteAntigoCpf", gerenteAntigoCpf != null ? gerenteAntigoCpf.trim() : null);
        payloadMap.put("dataAlteracao", agora.toString());

        EventoConta evento = new EventoConta();
        evento.setId(UUID.randomUUID().toString());
        evento.setObjetoId(numeroConta.trim());
        evento.setTipo("GerenteAlterado");
        evento.setPayload(converterParaJson(payloadMap));
        evento.setVersao(proximaVersao);
        evento.setTimestamp(agora);

        repository.save(evento);
        publicarEventoCqrs(evento);

        log.info("Conta {} atribuída ao novo gerente {} (gerente anterior: {})", numeroConta, novoGerenteCpf, gerenteAntigoCpf);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("numeroConta", numeroConta.trim());
        resultado.put("gerenteCpf", novoGerenteCpf.trim());
        resultado.put("status", "ATRIBUIDO");
        return resultado;
    }

    /**
     * SAGA Inserir Gerente (R13 - Compensação): Reassocia a conta ao gerente original.
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensarAtribuicaoConta(String numeroConta, String gerenteOriginalCpf) {
        if (numeroConta == null || gerenteOriginalCpf == null) {
            return;
        }
        atribuirConta(numeroConta, gerenteOriginalCpf, null);
        log.warn("Compensação SAGA executada: Conta {} reassociada ao gerente original {}", numeroConta, gerenteOriginalCpf);
    }

    /**
     * SAGA Aprovar Cliente (R9 - Passo 6): Cria nova conta e vincula ao gerente.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> criarConta(String cpfCliente, String gerenteCpf, String salarioStr) {
        if (cpfCliente == null || cpfCliente.isBlank()) {
            throw new IllegalArgumentException("CPF do cliente é obrigatório para criação de conta.");
        }
        if (gerenteCpf == null || gerenteCpf.isBlank()) {
            throw new IllegalArgumentException("CPF do gerente é obrigatório para criação de conta.");
        }

        String numeroConta;
        do {
            numeroConta = String.format("%04d", random.nextInt(10000));
        } while (repository.existsByObjetoId(numeroConta));

        LocalDateTime agora = LocalDateTime.now();
        BigDecimal salario = (salarioStr != null && !salarioStr.isBlank()) ? new BigDecimal(salarioStr) : BigDecimal.ZERO;
        BigDecimal limite = salario.compareTo(new BigDecimal("2000")) >= 0 ? salario.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("cpfCliente", cpfCliente.trim());
        payloadMap.put("gerenteCpf", gerenteCpf.trim());
        payloadMap.put("saldo", "0.00");
        payloadMap.put("salario", salario.setScale(2, RoundingMode.HALF_UP).toString());
        payloadMap.put("limite", limite.setScale(2, RoundingMode.HALF_UP).toString());
        payloadMap.put("dataCriacao", agora.toString());

        String payloadJson = converterParaJson(payloadMap);

        EventoConta evento = new EventoConta();
        evento.setId(UUID.randomUUID().toString());
        evento.setObjetoId(numeroConta);
        evento.setTipo("Criado");
        evento.setPayload(payloadJson);
        evento.setVersao(1);
        evento.setTimestamp(agora);

        repository.save(evento);
        log.info("Conta {} criada com sucesso para o cliente {} vinculada ao gerente {}", numeroConta, cpfCliente, gerenteCpf);

        publicarEventoCqrs(evento);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("numeroConta", numeroConta);
        resultado.put("cpfCliente", cpfCliente.trim());
        resultado.put("gerenteCpf", gerenteCpf.trim());
        resultado.put("saldo", "0.00");
        resultado.put("limite", limite.setScale(2, RoundingMode.HALF_UP).toString());
        return resultado;
    }

    /**
     * SAGA Compensação de Criação de Conta (R9).
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensarCriacaoConta(String numeroConta, String cpfCliente) {
        if (numeroConta != null && !numeroConta.isBlank()) {
            repository.deleteByObjetoId(numeroConta.trim());
            log.warn("Compensação SAGA executada: Conta {} removida do Event Store.", numeroConta);
            return;
        }

        if (cpfCliente != null && !cpfCliente.isBlank()) {
            List<EventoConta> eventos = repository.findByTipoIn(List.of("Criado", "CRIADO"));
            for (EventoConta evento : eventos) {
                JsonNode node = parseJsonNode(evento.getPayload());
                String cpf = node.has("cpfCliente") ? node.get("cpfCliente").asText() : (node.has("cpf") ? node.get("cpf").asText() : null);
                if (cpfCliente.trim().equals(cpf)) {
                    repository.deleteByObjetoId(evento.getObjetoId());
                    log.warn("Compensação SAGA executada: Conta {} removida para o cliente {}.", evento.getObjetoId(), cpfCliente);
                }
            }
        }
    }

    /**
     * Requisito R6: Transferência Atômica entre contas com replay dinâmico,
     * posse obrigatória (X-User-CPF) e novo replay em conflito de versão (concorrência otimista).
     */
    public TransferenciaResponseDTO transferir(String contaOrigem, TransferenciaRequestDTO dto, String xUserCpf) {
        if (contaOrigem == null || contaOrigem.trim().isEmpty()) {
            throw new TransferenciaInvalidaException("Conta de origem é obrigatória.");
        }
        if (dto == null) {
            throw new TransferenciaInvalidaException("Dados da transferência são obrigatórios.");
        }
        String contaDestino = dto.getContaDestino();
        if (contaDestino == null || contaDestino.trim().isEmpty()) {
            throw new TransferenciaInvalidaException("Conta de destino é obrigatória.");
        }
        if (contaOrigem.trim().equals(contaDestino.trim())) {
            throw new TransferenciaInvalidaException("A conta de destino não pode ser igual à conta de origem.");
        }

        if (!formatoValorValido(dto.getValor())) {
            throw new TransferenciaInvalidaException("Valor da transferência inválido.");
        }
        BigDecimal valorTransferencia = new BigDecimal(dto.getValor());

        if (valorTransferencia.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransferenciaInvalidaException("O valor da transferência deve ser maior que zero.");
        }

        int tentativas = 0;
        while (tentativas < MAX_TENTATIVAS_REPLAY) {
            tentativas++;
            try {
                return executarEmTransacao(() -> executarTransferencia(contaOrigem, contaDestino, dto, valorTransferencia, xUserCpf));
            } catch (DataIntegrityViolationException ex) {
                log.warn("Conflito de versão detectado na transferência (tentativa {}/{}). Realizando novo replay...",
                        tentativas, MAX_TENTATIVAS_REPLAY);
                if (tentativas >= MAX_TENTATIVAS_REPLAY) {
                    throw ex;
                }
                pausarBrevemente(tentativas);
            }
        }

        throw new IllegalStateException("Falha ao executar transferência.");
    }

    private TransferenciaResponseDTO executarTransferencia(
            String contaOrigem,
            String contaDestino,
            TransferenciaRequestDTO dto,
            BigDecimal valorTransferencia,
            String xUserCpf
    ) {
        // Novo replay dos eventos da conta de origem
        List<EventoConta> eventosOrigem = repository.findByObjetoIdOrderByVersaoAsc(contaOrigem.trim());
        if (eventosOrigem.isEmpty()) {
            throw new ContaNaoEncontradaException("Conta de origem não encontrada: " + contaOrigem);
        }

        EstadoConta estadoOrigem = reconstruirEstadoPorReplay(eventosOrigem);

        // Posse obrigatória (R6): validação do titular
        validarPosse(estadoOrigem, xUserCpf);

        // Validação de saldo estritamente via replay (R6 -> 422 em saldo insuficiente)
        if (estadoOrigem.getSaldo().compareTo(valorTransferencia) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente para realizar a transferência.");
        }

        // Novo replay dos eventos da conta de destino
        List<EventoConta> eventosDestino = repository.findByObjetoIdOrderByVersaoAsc(contaDestino.trim());
        if (eventosDestino.isEmpty()) {
            throw new ContaNaoEncontradaException("Conta de destino não encontrada: " + contaDestino);
        }
        EstadoConta estadoDestino = reconstruirEstadoPorReplay(eventosDestino);

        int proximaVersaoOrigem = eventosOrigem.get(eventosOrigem.size() - 1).getVersao() + 1;
        int proximaVersaoDestino = eventosDestino.get(eventosDestino.size() - 1).getVersao() + 1;

        LocalDateTime agora = LocalDateTime.now();
        String valorStr = valorTransferencia.setScale(2, RoundingMode.HALF_UP).toString();

        String cpfOrigem = dto.getCpfOrigem() != null ? dto.getCpfOrigem() : estadoOrigem.getCpfCliente();
        String cpfDestino = dto.getCpfDestino() != null ? dto.getCpfDestino() : estadoDestino.getCpfCliente();
        String nomeOrigem = dto.getNomeOrigem();
        String nomeDestino = dto.getNomeDestino();

        Map<String, Object> payloadOrigemMap = new HashMap<>();
        payloadOrigemMap.put("valor", valorStr);
        payloadOrigemMap.put("contaOrigem", contaOrigem.trim());
        payloadOrigemMap.put("contaDestino", contaDestino.trim());
        payloadOrigemMap.put("cpfOrigem", cpfOrigem);
        payloadOrigemMap.put("nomeOrigem", nomeOrigem);
        payloadOrigemMap.put("cpfDestino", cpfDestino);
        payloadOrigemMap.put("nomeDestino", nomeDestino);

        Map<String, Object> payloadDestinoMap = new HashMap<>(payloadOrigemMap);

        String payloadOrigemJson = converterParaJson(payloadOrigemMap);
        String payloadDestinoJson = converterParaJson(payloadDestinoMap);

        EventoConta eventoOrigem = new EventoConta();
        eventoOrigem.setId(UUID.randomUUID().toString());
        eventoOrigem.setObjetoId(contaOrigem.trim());
        eventoOrigem.setTipo("TransferênciaOrigem");
        eventoOrigem.setPayload(payloadOrigemJson);
        eventoOrigem.setVersao(proximaVersaoOrigem);
        eventoOrigem.setTimestamp(agora);

        EventoConta eventoDestino = new EventoConta();
        eventoDestino.setId(UUID.randomUUID().toString());
        eventoDestino.setObjetoId(contaDestino.trim());
        eventoDestino.setTipo("TransferênciaDestino");
        eventoDestino.setPayload(payloadDestinoJson);
        eventoDestino.setVersao(proximaVersaoDestino);
        eventoDestino.setTimestamp(agora);

        repository.save(eventoOrigem);
        repository.save(eventoDestino);
        repository.flush();

        publicarEventoCqrs(eventoOrigem);
        publicarEventoCqrs(eventoDestino);

        TransferenciaResponseDTO resposta = new TransferenciaResponseDTO();
        resposta.setIdOrigem(eventoOrigem.getId());
        resposta.setIdDestino(eventoDestino.getId());
        resposta.setContaOrigem(contaOrigem.trim());
        resposta.setContaDestino(contaDestino.trim());
        resposta.setValor(valorStr);
        resposta.setDataHora(agora);
        resposta.setMensagem("Transferência realizada com sucesso.");

        resposta.addLink("self", "http://localhost:8080/contas/" + contaOrigem.trim() + "/transferencia");
        resposta.addLink("deposito", "http://localhost:8080/contas/" + contaOrigem.trim() + "/deposito");
        resposta.addLink("saque", "http://localhost:8080/contas/" + contaOrigem.trim() + "/saque");
        resposta.addLink("extrato", "http://localhost:8080/contas/" + contaOrigem.trim() + "/extrato");

        return resposta;
    }

    /**
     * Requisito R5: Saque com replay dinâmico, posse obrigatória (X-User-CPF),
     * 422 em saldo insuficiente e novo replay em conflito de versão.
     */
    public MovimentacaoResponseDTO sacar(String numeroConta, MovimentacaoRequestDTO dto, String xUserCpf) {
        if (numeroConta == null || numeroConta.trim().isEmpty()) {
            throw new IllegalArgumentException("Número da conta é obrigatório.");
        }
        if (dto == null || dto.getValor() == null || dto.getValor().trim().isEmpty()) {
            throw new IllegalArgumentException("Valor do saque é obrigatório.");
        }

        if (!formatoValorValido(dto.getValor().trim())) {
            throw new IllegalArgumentException("Valor do saque inválido.");
        }
        BigDecimal valorSaque = new BigDecimal(dto.getValor().trim());

        if (valorSaque.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor do saque deve ser maior que zero.");
        }

        int tentativas = 0;
        while (tentativas < MAX_TENTATIVAS_REPLAY) {
            tentativas++;
            try {
                return executarEmTransacao(() -> executarSaque(numeroConta, valorSaque, xUserCpf));
            } catch (DataIntegrityViolationException ex) {
                log.warn("Conflito de versão detectado no saque (tentativa {}/{}). Realizando novo replay...",
                        tentativas, MAX_TENTATIVAS_REPLAY);
                if (tentativas >= MAX_TENTATIVAS_REPLAY) {
                    throw ex;
                }
                pausarBrevemente(tentativas);
            }
        }

        throw new IllegalStateException("Falha ao executar saque.");
    }

    private MovimentacaoResponseDTO executarSaque(String numeroConta, BigDecimal valorSaque, String xUserCpf) {
        List<EventoConta> eventos = repository.findByObjetoIdOrderByVersaoAsc(numeroConta.trim());
        if (eventos.isEmpty()) {
            throw new ContaNaoEncontradaException("Conta não encontrada: " + numeroConta);
        }

        EstadoConta estado = reconstruirEstadoPorReplay(eventos);

        // Posse obrigatória (R5)
        validarPosse(estado, xUserCpf);

        // Validação de saldo estritamente via replay (R5 -> 422 em saldo insuficiente)
        if (estado.getSaldo().compareTo(valorSaque) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente para realizar o saque.");
        }

        int proximaVersao = eventos.get(eventos.size() - 1).getVersao() + 1;
        LocalDateTime agora = LocalDateTime.now();
        String valorStr = valorSaque.setScale(2, RoundingMode.HALF_UP).toString();

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("valor", valorStr);
        payloadMap.put("numeroConta", numeroConta.trim());
        payloadMap.put("dataHora", agora.toString());

        EventoConta evento = new EventoConta();
        evento.setId(UUID.randomUUID().toString());
        evento.setObjetoId(numeroConta.trim());
        evento.setTipo("Saque");
        evento.setPayload(converterParaJson(payloadMap));
        evento.setVersao(proximaVersao);
        evento.setTimestamp(agora);

        repository.save(evento);
        repository.flush();
        publicarEventoCqrs(evento);

        // Sem o novo saldo: o front-end reconsulta o lado query (CQRS)
        MovimentacaoResponseDTO resposta = new MovimentacaoResponseDTO();
        resposta.setId(evento.getId());
        resposta.setNumeroConta(numeroConta.trim());
        resposta.setTipo("SAQUE");
        resposta.setValor(valorStr);
        resposta.setDataHora(agora);
        resposta.setMensagem("Saque realizado com sucesso.");

        resposta.addLink("self", "http://localhost:8080/contas/" + numeroConta.trim() + "/saque");
        resposta.addLink("deposito", "http://localhost:8080/contas/" + numeroConta.trim() + "/deposito");
        resposta.addLink("transferencia", "http://localhost:8080/contas/" + numeroConta.trim() + "/transferencia");
        resposta.addLink("extrato", "http://localhost:8080/contas/" + numeroConta.trim() + "/extrato");

        return resposta;
    }

    /**
     * Depósito (R4) com replay dinâmico, posse obrigatória (X-User-CPF)
     * e novo replay em conflito de versão.
     */
    public MovimentacaoResponseDTO depositar(String numeroConta, MovimentacaoRequestDTO dto, String xUserCpf) {
        if (numeroConta == null || numeroConta.trim().isEmpty()) {
            throw new IllegalArgumentException("Número da conta é obrigatório.");
        }
        if (dto == null || dto.getValor() == null || dto.getValor().trim().isEmpty()) {
            throw new IllegalArgumentException("Valor do depósito é obrigatório.");
        }

        if (!formatoValorValido(dto.getValor().trim())) {
            throw new IllegalArgumentException("Valor do depósito inválido.");
        }
        BigDecimal valorDeposito = new BigDecimal(dto.getValor().trim());

        if (valorDeposito.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor do depósito deve ser maior que zero.");
        }

        int tentativas = 0;
        while (tentativas < MAX_TENTATIVAS_REPLAY) {
            tentativas++;
            try {
                return executarEmTransacao(() -> executarDeposito(numeroConta, valorDeposito, xUserCpf));
            } catch (DataIntegrityViolationException ex) {
                log.warn("Conflito de versão detectado no depósito (tentativa {}/{}). Realizando novo replay...",
                        tentativas, MAX_TENTATIVAS_REPLAY);
                if (tentativas >= MAX_TENTATIVAS_REPLAY) {
                    throw ex;
                }
                pausarBrevemente(tentativas);
            }
        }

        throw new IllegalStateException("Falha ao executar depósito.");
    }

    private MovimentacaoResponseDTO executarDeposito(String numeroConta, BigDecimal valorDeposito, String xUserCpf) {
        List<EventoConta> eventos = repository.findByObjetoIdOrderByVersaoAsc(numeroConta.trim());
        if (eventos.isEmpty()) {
            throw new ContaNaoEncontradaException("Conta não encontrada: " + numeroConta);
        }

        EstadoConta estado = reconstruirEstadoPorReplay(eventos);

        // Posse obrigatória (R4): ninguém deposita na conta de outro cliente
        validarPosse(estado, xUserCpf);

        int proximaVersao = eventos.get(eventos.size() - 1).getVersao() + 1;
        LocalDateTime agora = LocalDateTime.now();
        String valorStr = valorDeposito.setScale(2, RoundingMode.HALF_UP).toString();

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("valor", valorStr);
        payloadMap.put("numeroConta", numeroConta.trim());
        payloadMap.put("dataHora", agora.toString());

        EventoConta evento = new EventoConta();
        evento.setId(UUID.randomUUID().toString());
        evento.setObjetoId(numeroConta.trim());
        evento.setTipo("Depósito");
        evento.setPayload(converterParaJson(payloadMap));
        evento.setVersao(proximaVersao);
        evento.setTimestamp(agora);

        repository.save(evento);
        repository.flush();
        publicarEventoCqrs(evento);

        // Sem o novo saldo: o front-end reconsulta o lado query (CQRS)
        MovimentacaoResponseDTO resposta = new MovimentacaoResponseDTO();
        resposta.setId(evento.getId());
        resposta.setNumeroConta(numeroConta.trim());
        resposta.setTipo("DEPOSITO");
        resposta.setValor(valorStr);
        resposta.setDataHora(agora);
        resposta.setMensagem("Depósito realizado com sucesso.");

        resposta.addLink("self", "http://localhost:8080/contas/" + numeroConta.trim() + "/deposito");
        resposta.addLink("saque", "http://localhost:8080/contas/" + numeroConta.trim() + "/saque");
        resposta.addLink("transferencia", "http://localhost:8080/contas/" + numeroConta.trim() + "/transferencia");
        resposta.addLink("extrato", "http://localhost:8080/contas/" + numeroConta.trim() + "/extrato");

        return resposta;
    }

    @Transactional(readOnly = true)
    public ContaDTO buscarContaPorNumero(String numeroConta) {
        List<EventoConta> eventos = repository.findByObjetoIdOrderByVersaoAsc(numeroConta);
        if (eventos.isEmpty()) {
            throw new ContaNaoEncontradaException("Conta não encontrada: " + numeroConta);
        }
        EstadoConta estado = reconstruirEstadoPorReplay(eventos);
        ContaDTO dto = new ContaDTO(numeroConta, estado.getCpfCliente(), estado.getGerenteCpf());
        dto.addLink("self", "http://localhost:8080/contas/" + numeroConta);
        dto.addLink("deposito", "http://localhost:8080/contas/" + numeroConta + "/deposito");
        dto.addLink("saque", "http://localhost:8080/contas/" + numeroConta + "/saque");
        dto.addLink("extrato", "http://localhost:8080/contas/" + numeroConta + "/extrato");
        return dto;
    }

    private Map<String, String> mapearGerenteAtualPorConta() {
        List<EventoConta> eventos = repository.findByTipoIn(List.of("Criado", "CRIADO", "GerenteAlterado", "GERENTEALTERADO"));
        Map<String, String> gerenteAtualPorConta = new HashMap<>();
        eventos.stream()
                .sorted(Comparator.comparing(EventoConta::getVersao))
                .forEach(evento -> {
                    JsonNode node = parseJsonNode(evento.getPayload());
                    String gerenteCpf = null;
                    if (node.has("gerenteCpf")) {
                        gerenteCpf = node.get("gerenteCpf").asText();
                    } else if (node.has("cpfGerente")) {
                        gerenteCpf = node.get("cpfGerente").asText();
                    }
                    if (gerenteCpf != null && !gerenteCpf.isBlank()) {
                        gerenteAtualPorConta.put(evento.getObjetoId(), gerenteCpf.trim());
                    }
                });
        return gerenteAtualPorConta;
    }

    public EstadoConta reconstruirEstadoPorReplay(List<EventoConta> eventos) {
        EstadoConta estado = new EstadoConta();
        for (EventoConta evento : eventos) {
            String tipoNormalizado = normalizarTipoEvento(evento.getTipo());
            JsonNode payloadNode = parseJsonNode(evento.getPayload());

            switch (tipoNormalizado) {
                case "CRIADO" -> {
                    if (payloadNode.has("saldo")) {
                        estado.adicionarSaldo(new BigDecimal(payloadNode.get("saldo").asText()));
                    }
                    if (payloadNode.has("cpfCliente")) {
                        estado.setCpfCliente(payloadNode.get("cpfCliente").asText());
                    } else if (payloadNode.has("cpf")) {
                        estado.setCpfCliente(payloadNode.get("cpf").asText());
                    }
                    if (payloadNode.has("gerenteCpf")) {
                        estado.setGerenteCpf(payloadNode.get("gerenteCpf").asText());
                    } else if (payloadNode.has("cpfGerente")) {
                        estado.setGerenteCpf(payloadNode.get("cpfGerente").asText());
                    }
                }
                case "DEPOSITO", "TRANSFERENCIADESTINO" -> {
                    if (payloadNode.has("valor")) {
                        estado.adicionarSaldo(new BigDecimal(payloadNode.get("valor").asText()));
                    }
                }
                case "SAQUE", "TRANSFERENCIAORIGEM" -> {
                    if (payloadNode.has("valor")) {
                        estado.subtrairSaldo(new BigDecimal(payloadNode.get("valor").asText()));
                    }
                }
                case "GERENTEALTERADO" -> {
                    if (payloadNode.has("gerenteCpf")) {
                        estado.setGerenteCpf(payloadNode.get("gerenteCpf").asText());
                    } else if (payloadNode.has("cpfGerente")) {
                        estado.setGerenteCpf(payloadNode.get("cpfGerente").asText());
                    }
                }
                default -> {}
            }
        }
        return estado;
    }

    private void publicarEventoCqrs(EventoConta evento) {
        if (rabbitTemplate != null) {
            try {
                rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_CONTA_EVENTS, evento);
            } catch (Exception e) {
                log.warn("Não foi possível publicar evento CQRS na fila {}: {}", RabbitMQConfig.FILA_CONTA_EVENTS, e.getMessage());
            }
        }
    }

    private boolean formatoValorValido(String valor) {
        return valor != null && FORMATO_VALOR.matcher(valor).matches();
    }

    // Posse obrigatória (R4, R5, R6): só o titular opera a conta
    private void validarPosse(EstadoConta estado, String xUserCpf) {
        if (xUserCpf == null || xUserCpf.trim().isEmpty()) {
            throw new AcessoNegadoException("Usuário não autenticado ou posse da conta não informada.");
        }
        if (estado.getCpfCliente() == null || !xUserCpf.trim().equals(estado.getCpfCliente().trim())) {
            throw new AcessoNegadoException("Usuário não autorizado a realizar operações nesta conta.");
        }
    }

    private void pausarBrevemente(int tentativa) {
        try {
            Thread.sleep(15L * tentativa);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String normalizarTipoEvento(String tipo) {
        if (tipo == null) return "";
        String desacentuado = Normalizer.normalize(tipo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return desacentuado.toUpperCase().replaceAll("[^A-Z]", "");
    }

    private JsonNode parseJsonNode(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String converterParaJson(Object objeto) {
        try {
            return objectMapper.writeValueAsString(objeto);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao serializar payload do evento.", e);
        }
    }

    public static class EstadoConta {
        private BigDecimal saldo = BigDecimal.ZERO;
        private String cpfCliente;
        private String gerenteCpf;

        public void adicionarSaldo(BigDecimal valor) {
            if (valor != null) {
                this.saldo = this.saldo.add(valor);
            }
        }

        public void subtrairSaldo(BigDecimal valor) {
            if (valor != null) {
                this.saldo = this.saldo.subtract(valor);
            }
        }

        public BigDecimal getSaldo() {
            return saldo;
        }

        public String getCpfCliente() {
            return cpfCliente;
        }

        public void setCpfCliente(String cpfCliente) {
            this.cpfCliente = cpfCliente;
        }

        public String getGerenteCpf() {
            return gerenteCpf;
        }

        public void setGerenteCpf(String gerenteCpf) {
            this.gerenteCpf = gerenteCpf;
        }
    }
}
