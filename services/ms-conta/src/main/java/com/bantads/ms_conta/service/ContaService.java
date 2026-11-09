package com.bantads.ms_conta.service;

import com.bantads.ms_conta.config.RabbitMQConfig;
import com.bantads.ms_conta.dto.ContaDTO;
import com.bantads.ms_conta.dto.ExtratoResponseDTO;
import com.bantads.ms_conta.dto.ItemExtratoDTO;
import com.bantads.ms_conta.dto.MovimentacaoRequestDTO;
import com.bantads.ms_conta.dto.MovimentacaoResponseDTO;
import com.bantads.ms_conta.dto.TransferenciaRequestDTO;
import com.bantads.ms_conta.dto.TransferenciaResponseDTO;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class ContaService {

    private static final Logger log = LoggerFactory.getLogger(ContaService.class);
    private static final int MAX_TENTATIVAS_REPLAY = 3;
    private static final long MAX_DIAS_EXTRATO = 365;

    // Até 15 dígitos inteiros e 2 casas decimais, o que cabe no NUMERIC(19, 4). Também barra a notação
    // científica: "1E-99999999" passa no new BigDecimal() e prende a thread por mais de 1 min no setScale
    private static final Pattern FORMATO_VALOR = Pattern.compile("\\d{1,15}(\\.\\d{1,2})?");

    private final EventoContaRepository repository;
    private final ContaQueryRepository contaQueryRepository;
    private final MovimentacaoQueryRepository movimentacaoQueryRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Random random = new Random();

    public ContaService(
            EventoContaRepository repository,
            ObjectMapper objectMapper,
            RabbitTemplate rabbitTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this(repository, null, null, objectMapper, rabbitTemplate, transactionManager);
    }

    @Autowired
    public ContaService(
            EventoContaRepository repository,
            @Autowired(required = false) ContaQueryRepository contaQueryRepository,
            @Autowired(required = false) MovimentacaoQueryRepository movimentacaoQueryRepository,
            ObjectMapper objectMapper,
            @Autowired(required = false) RabbitTemplate rabbitTemplate,
            @Autowired(required = false) PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.contaQueryRepository = contaQueryRepository;
        this.movimentacaoQueryRepository = movimentacaoQueryRepository;
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

    // Optimistic locking (enunciado 5.10): quem perde a disputa pela versão refaz o replay e revalida.
    // ConcurrencyFailureException cobre o deadlock do Postgres, que não chega como DataIntegrityViolation
    private <T> T executarComNovoReplay(String operacao, Supplier<T> acao) {
        for (int tentativa = 1; ; tentativa++) {
            try {
                return executarEmTransacao(acao);
            } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
                if (tentativa >= MAX_TENTATIVAS_REPLAY) {
                    throw ex;
                }
                log.warn("Conflito de versão detectado no {} (tentativa {}/{}). Realizando novo replay...",
                        operacao, tentativa, MAX_TENTATIVAS_REPLAY);
                pausarBrevemente(tentativa);
            }
        }
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

        // LinkedHashMap: no empate fica o primeiro da lista recebida, e a escolha não muda de uma execução para outra
        Map<String, Long> contagemPorGerente = new LinkedHashMap<>();
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

        // Ordem estável nos empates (mesma quantidade de contas, mesmo saldo): ordem da lista de gerentes
        // e, dentro de cada gerente, número da conta
        Map<String, List<String>> contasPorGerente = new LinkedHashMap<>();
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
        contasPorGerente.values().forEach(Collections::sort);

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
        return atribuirConta(numeroConta, novoGerenteCpf, gerenteAntigoCpf, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> atribuirConta(String numeroConta, String novoGerenteCpf, String gerenteAntigoCpf, String sagaId) {
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
        if (sagaId != null) {
            payloadMap.put("sagaId", sagaId);
        }

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

    // SAGA Remover Gerente (R15): todas as contas do removido vao para o ativo com menos clientes, um GerenteAlterado por conta
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transferirContas(String sagaId, String gerenteRemovidoCpf, List<String> gerentesAtivos) {
        if (gerenteRemovidoCpf == null || gerenteRemovidoCpf.isBlank()) {
            throw new IllegalArgumentException("CPF do gerente removido é obrigatório.");
        }

        List<String> contas = mapearGerenteAtualPorConta().entrySet().stream()
                .filter(entry -> entry.getValue().equals(gerenteRemovidoCpf.trim()))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        List<String> cpfsClientes = new ArrayList<>();
        if (!contas.isEmpty()) {
            // O removido não pode receber as próprias contas, mesmo que ainda apareça na lista de ativos
            List<String> candidatos = gerentesAtivos == null ? List.of() : gerentesAtivos.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(cpf -> !cpf.isEmpty() && !cpf.equals(gerenteRemovidoCpf.trim()))
                    .distinct()
                    .toList();
            if (candidatos.isEmpty()) {
                throw new IllegalArgumentException("Não há outro gerente ativo para receber as contas do gerente removido.");
            }
            String novoGerenteCpf = identificarGerenteComMenosClientes(candidatos);
            for (String numeroConta : contas) {
                atribuirConta(numeroConta, novoGerenteCpf, gerenteRemovidoCpf, sagaId);
                EstadoConta estado = reconstruirEstadoPorReplay(repository.findByObjetoIdOrderByVersaoAsc(numeroConta));
                cpfsClientes.add(estado.getCpfCliente());
            }
        }

        log.info("{} conta(s) do gerente {} transferida(s) na SAGA {}", contas.size(), gerenteRemovidoCpf, sagaId);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("contas", contas);
        resultado.put("cpfsClientes", cpfsClientes);
        return resultado;
    }

    // compensacao da R15: as contas movidas nesta SAGA sao achadas pelo sagaId gravado no evento
    @Transactional(rollbackFor = Exception.class)
    public void compensarTransferenciaContas(String sagaId, String gerenteOriginalCpf) {
        if (sagaId == null || gerenteOriginalCpf == null) {
            return;
        }
        List<String> contas = repository.findByTipoIn(List.of("GerenteAlterado", "GERENTEALTERADO")).stream()
                .filter(evento -> {
                    JsonNode node = parseJsonNode(evento.getPayload());
                    return node.has("sagaId") && sagaId.equals(node.get("sagaId").asText());
                })
                .map(EventoConta::getObjetoId)
                .distinct()
                .toList();
        for (String numeroConta : contas) {
            atribuirConta(numeroConta, gerenteOriginalCpf, null);
        }
        log.warn("Compensação SAGA executada: {} conta(s) reassociada(s) ao gerente original {}", contas.size(), gerenteOriginalCpf);
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
        // R1: cada cliente tem uma só conta. A idempotência por (sagaId, tipo) não cobre uma segunda SAGA
        // para o mesmo CPF, e duas contas quebrariam a busca da conta pelo CPF (R3, R7)
        Optional<String> contaExistente = buscarNumeroContaNoEventStore(cpfCliente.trim());
        if (contaExistente.isPresent()) {
            throw new IllegalStateException("O cliente " + cpfCliente.trim() + " já possui a conta " + contaExistente.get() + ".");
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
            String num = numeroConta.trim();
            repository.deleteByObjetoId(num);
            removerContaDoLadoQuery(num);
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
                    removerContaDoLadoQuery(evento.getObjetoId());
                    log.warn("Compensação SAGA executada: Conta {} removida para o cliente {}.", evento.getObjetoId(), cpfCliente);
                }
            }
        }
    }

    private void removerContaDoLadoQuery(String numeroConta) {
        if (movimentacaoQueryRepository != null) {
            movimentacaoQueryRepository.deleteByNumeroConta(numeroConta);
        }
        if (contaQueryRepository != null && contaQueryRepository.existsById(numeroConta)) {
            contaQueryRepository.deleteById(numeroConta);
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
            throw new RegraNegocioException("A conta de destino não pode ser igual à conta de origem.");
        }

        if (!formatoValorValido(dto.getValor())) {
            throw new TransferenciaInvalidaException("Valor da transferência inválido.");
        }
        BigDecimal valorTransferencia = new BigDecimal(dto.getValor());

        if (valorTransferencia.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransferenciaInvalidaException("O valor da transferência deve ser maior que zero.");
        }

        return executarComNovoReplay("transferência",
                () -> executarTransferencia(contaOrigem, contaDestino, dto, valorTransferencia, xUserCpf));
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
            // 422 e não 404: o recurso da URL (a conta de origem) existe; o que falha é a regra do R6
            throw new RegraNegocioException("Conta de destino não encontrada: " + contaDestino);
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

        // Grava sempre na ordem do número da conta. Em duas transferências cruzadas (A→B e B→A), as duas
        // disputam primeiro a mesma chave (objeto_id, versao) e uma perde por versão duplicada, que tem
        // novo replay; na ordem origem→destino cada uma travaria a chave que a outra espera (deadlock)
        List<EventoConta> novosEventos = new ArrayList<>(List.of(eventoOrigem, eventoDestino));
        novosEventos.sort(Comparator.comparing(EventoConta::getObjetoId));
        novosEventos.forEach(repository::save);
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
        resposta.setOrigem(new TransferenciaResponseDTO.Participante(contaOrigem.trim(), cpfOrigem, nomeOrigem));
        resposta.setDestino(new TransferenciaResponseDTO.Participante(contaDestino.trim(), cpfDestino, nomeDestino));

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

        return executarComNovoReplay("saque", () -> executarSaque(numeroConta, valorSaque, xUserCpf));
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

        return executarComNovoReplay("depósito", () -> executarDeposito(numeroConta, valorDeposito, xUserCpf));
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
        if (contaQueryRepository != null) {
            Optional<ContaQuery> queryOpt = contaQueryRepository.findById(numeroConta);
            if (queryOpt.isPresent()) {
                return montarContaDTO(queryOpt.get());
            }
        }
        List<EventoConta> eventos = repository.findByObjetoIdOrderByVersaoAsc(numeroConta);
        if (eventos.isEmpty()) {
            throw new ContaNaoEncontradaException("Conta não encontrada: " + numeroConta);
        }
        EstadoConta estado = reconstruirEstadoPorReplay(eventos);
        return montarContaDTO(numeroConta, estado.getCpfCliente(), estado.getGerenteCpf(), estado.getSaldo());
    }

    @Transactional
    public ContaDTO buscarContaPorCpf(String xUserCpf) {
        if (xUserCpf == null || xUserCpf.trim().isEmpty()) {
            throw new AcessoNegadoException("Usuário não autenticado ou posse da conta não informada.");
        }
        String cpfLimpo = xUserCpf.trim();

        if (contaQueryRepository != null) {
            Optional<ContaQuery> contaQueryOpt = contaQueryRepository.findByCpfCliente(cpfLimpo);
            if (contaQueryOpt.isPresent()) {
                String numeroConta = contaQueryOpt.get().getNumeroConta();
                sincronizarLadoQuerySeNecessario(numeroConta);
                return buscarContaPorNumero(numeroConta);
            }
        }

        // Fallback pelo Event Store caso a projeção assíncrona ainda não tenha processado
        return buscarNumeroContaNoEventStore(cpfLimpo)
                .map(this::buscarContaPorNumero)
                .orElseThrow(() -> new ContaNaoEncontradaException("Conta não encontrada para o cliente: " + cpfLimpo));
    }

    @Transactional(readOnly = true)
    public List<ContaDTO> listarContas() {
        if (contaQueryRepository != null) {
            // Ordem estável por número; quem ordena por nome do cliente (R11, R16) é o Gateway, que tem o nome
            return contaQueryRepository.findAll(Sort.by("numeroConta")).stream().map(this::montarContaDTO).toList();
        }
        return repository.findByTipoIn(List.of("Criado", "CRIADO")).stream()
                .map(EventoConta::getObjetoId)
                .distinct()
                .sorted()
                .map(this::buscarContaPorNumero)
                .toList();
    }

    // Lado command: o evento Criado guarda o CPF do titular. Em ordem de número, para o resultado ser estável
    private Optional<String> buscarNumeroContaNoEventStore(String cpfCliente) {
        return repository.findByTipoIn(List.of("Criado", "CRIADO")).stream()
                .filter(evento -> {
                    JsonNode node = parseJsonNode(evento.getPayload());
                    String cpf = node.has("cpfCliente") ? node.get("cpfCliente").asText()
                            : (node.has("cpf") ? node.get("cpf").asText() : null);
                    return cpf != null && cpfCliente.equals(cpf.trim());
                })
                .map(EventoConta::getObjetoId)
                .sorted()
                .findFirst();
    }

    private ContaDTO montarContaDTO(ContaQuery contaQuery) {
        return montarContaDTO(contaQuery.getNumeroConta(), contaQuery.getCpfCliente(), contaQuery.getGerenteCpf(), contaQuery.getSaldo());
    }

    private ContaDTO montarContaDTO(String numeroConta, String cpfCliente, String gerenteCpf, BigDecimal saldo) {
        ContaDTO dto = new ContaDTO(numeroConta, cpfCliente, gerenteCpf);
        dto.setSaldo(saldo.setScale(2, RoundingMode.HALF_UP).toString());
        dto.addLink("self", "http://localhost:8080/contas/" + numeroConta);
        dto.addLink("deposito", "http://localhost:8080/contas/" + numeroConta + "/deposito");
        dto.addLink("saque", "http://localhost:8080/contas/" + numeroConta + "/saque");
        dto.addLink("extrato", "http://localhost:8080/contas/" + numeroConta + "/extrato");
        return dto;
    }

    /**
     * Requisito R7: Consulta de extrato no lado Query pelo CPF do cliente autenticado (X-User-CPF).
     */
    @Transactional
    public ExtratoResponseDTO consultarExtratoPorCpf(String xUserCpf, String inicioStr, String fimStr) {
        if (xUserCpf == null || xUserCpf.trim().isEmpty()) {
            throw new AcessoNegadoException("Usuário não autenticado ou posse da conta não informada.");
        }
        String cpfLimpo = xUserCpf.trim();

        if (contaQueryRepository != null) {
            Optional<ContaQuery> contaQueryOpt = contaQueryRepository.findByCpfCliente(cpfLimpo);
            if (contaQueryOpt.isPresent()) {
                return consultarExtrato(contaQueryOpt.get().getNumeroConta(), inicioStr, fimStr, cpfLimpo);
            }
        }

        // Fallback pelo Event Store caso a projeção assíncrona ainda não tenha processado
        return buscarNumeroContaNoEventStore(cpfLimpo)
                .map(numeroConta -> consultarExtrato(numeroConta, inicioStr, fimStr, cpfLimpo))
                .orElseThrow(() -> new ContaNaoEncontradaException("Conta não encontrada para o cliente: " + cpfLimpo));
    }

    /**
     * Requisito R7: Consulta de extrato no lado Query da arquitetura CQRS por período (início e fim),
     * retornando saldo de abertura consolidado antes da data inicial, lista de movimentações (entradas e saídas)
     * e links HATEOAS.
     */
    @Transactional
    public ExtratoResponseDTO consultarExtrato(
            String numeroConta,
            String inicioStr,
            String fimStr,
            String xUserCpf
    ) {
        if (numeroConta == null || numeroConta.trim().isEmpty()) {
            throw new IllegalArgumentException("Número da conta é obrigatório.");
        }
        String num = numeroConta.trim();

        LocalDate fim = (fimStr != null && !fimStr.isBlank()) ? parseData(fimStr.trim(), "fim") : LocalDate.now();
        LocalDate inicio = (inicioStr != null && !inicioStr.isBlank()) ? parseData(inicioStr.trim(), "inicio") : fim.minusDays(30);

        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A data de início deve ser anterior ou igual à data de fim.");
        }
        // R7: intervalo máximo de 365 dias (2020-01-01 a 2020-12-31 ainda passa)
        if (ChronoUnit.DAYS.between(inicio, fim) > MAX_DIAS_EXTRATO) {
            throw new RegraNegocioException("O intervalo do extrato não pode passar de " + MAX_DIAS_EXTRATO + " dias.");
        }

        LocalDateTime dataHoraInicio = inicio.atStartOfDay();
        LocalDateTime dataHoraFim = fim.atTime(23, 59, 59, 999_999_999);

        sincronizarLadoQuerySeNecessario(num);

        if (contaQueryRepository != null && movimentacaoQueryRepository != null) {
            Optional<ContaQuery> contaQueryOpt = contaQueryRepository.findById(num);
            if (contaQueryOpt.isPresent()) {
                ContaQuery contaQuery = contaQueryOpt.get();
                EstadoConta estadoQuery = new EstadoConta();
                estadoQuery.setCpfCliente(contaQuery.getCpfCliente());
                estadoQuery.setGerenteCpf(contaQuery.getGerenteCpf());
                estadoQuery.adicionarSaldo(contaQuery.getSaldo());

                validarPosse(estadoQuery, xUserCpf);

                List<MovimentacaoQuery> todasMovs = movimentacaoQueryRepository
                        .findByNumeroContaOrderByDataHoraAscVersaoAsc(num);
                List<MovimentacaoQuery> movsAnteriores = movimentacaoQueryRepository
                        .findByNumeroContaAndDataHoraLessThanOrderByDataHoraAscVersaoAsc(num, dataHoraInicio);
                List<MovimentacaoQuery> movsPeriodo = movimentacaoQueryRepository
                        .findByNumeroContaAndDataHoraBetweenOrderByDataHoraAscVersaoAsc(num, dataHoraInicio, dataHoraFim);

                BigDecimal somaTodasMovs = somarMovimentacoesComSinal(todasMovs);
                BigDecimal saldoInicialCriacao = contaQuery.getSaldo().subtract(somaTodasMovs);
                BigDecimal saldoAbertura = saldoInicialCriacao.add(somarMovimentacoesComSinal(movsAnteriores));

                List<ItemExtratoDTO> itens = new ArrayList<>();
                for (MovimentacaoQuery mov : movsPeriodo) {
                    itens.add(new ItemExtratoDTO(
                            mov.getDataHora().toString(),
                            mov.getTipo(),
                            mov.getCpfOrigem(),
                            mov.getNomeOrigem(),
                            mov.getCpfDestino(),
                            mov.getNomeDestino(),
                            mov.getValor().setScale(2, RoundingMode.HALF_UP).toString()
                    ));
                }

                return montarExtratoResponse(num, saldoAbertura, contaQuery.getSaldo(), itens);
            }
        }

        // Fallback direto a partir dos eventos caso os repositórios query não estejam injetados
        List<EventoConta> eventos = repository.findByObjetoIdOrderByVersaoAsc(num);
        if (eventos.isEmpty()) {
            throw new ContaNaoEncontradaException("Conta não encontrada: " + num);
        }

        EstadoConta estado = reconstruirEstadoPorReplay(eventos);
        validarPosse(estado, xUserCpf);

        BigDecimal saldoAbertura = BigDecimal.ZERO;
        List<ItemExtratoDTO> itens = new ArrayList<>();

        for (EventoConta evento : eventos) {
            String tipoNorm = normalizarTipoEvento(evento.getTipo());
            JsonNode payload = parseJsonNode(evento.getPayload());
            LocalDateTime dataEvento = extrairDataHoraEvento(evento, payload);

            if ("CRIADO".equals(tipoNorm)) {
                if (payload.has("saldo")) {
                    saldoAbertura = saldoAbertura.add(new BigDecimal(payload.get("saldo").asText()));
                }
                continue;
            }

            MovimentacaoQuery mov = construirMovimentacaoQuery(evento, estado.getCpfCliente());
            if (mov == null) {
                continue;
            }

            if (dataEvento.isBefore(dataHoraInicio)) {
                if ("ENTRADA".equals(mov.getNatureza())) {
                    saldoAbertura = saldoAbertura.add(mov.getValor());
                } else {
                    saldoAbertura = saldoAbertura.subtract(mov.getValor());
                }
            } else if (!dataEvento.isAfter(dataHoraFim)) {
                itens.add(new ItemExtratoDTO(
                        mov.getDataHora().toString(),
                        mov.getTipo(),
                        mov.getCpfOrigem(),
                        mov.getNomeOrigem(),
                        mov.getCpfDestino(),
                        mov.getNomeDestino(),
                        mov.getValor().setScale(2, RoundingMode.HALF_UP).toString()
                ));
            }
        }

        return montarExtratoResponse(num, saldoAbertura, estado.getSaldo(), itens);
    }

    /**
     * Projeta de forma idempotente um EventoConta nas tabelas do lado Query (conta_query e movimentacao_query).
     * Entrada dos eventos que chegam de fora do Event Store (fila ms.conta.events e seed do reboot).
     */
    @Transactional
    public void projetarEventoNoLadoQuery(EventoConta evento) {
        projetarEvento(evento, true);
    }

    // recebidoDeFora = false quando o evento acabou de ser lido do Event Store pela sincronização: aí ele
    // existe com certeza e uma lacuna não dispara outra sincronização (evita recursão se o Event Store
    // tiver um buraco de versão)
    private void projetarEvento(EventoConta evento, boolean recebidoDeFora) {
        if (evento == null || evento.getVersao() == null
                || contaQueryRepository == null || movimentacaoQueryRepository == null) {
            return;
        }

        String numeroConta = evento.getObjetoId();
        String tipoNorm = normalizarTipoEvento(evento.getTipo());
        JsonNode payload = parseJsonNode(evento.getPayload());
        LocalDateTime dataHora = extrairDataHoraEvento(evento, payload);

        if ("CRIADO".equals(tipoNorm)) {
            ContaQuery conta = contaQueryRepository.findById(numeroConta).orElseGet(ContaQuery::new);
            if (conta.getUltimaVersao() != null && conta.getUltimaVersao() >= evento.getVersao()) {
                return;
            }
            // A compensação da R9 apaga a conta do Event Store e do lado query. Se o Criado ainda estava na
            // fila, projetá-lo agora recriaria no lado query uma conta que não existe mais
            if (recebidoDeFora && !repository.existsById(evento.getId())) {
                log.warn("Evento Criado {} da conta {} não está mais no Event Store (conta compensada). Ignorado.",
                        evento.getId(), numeroConta);
                return;
            }
            conta.setNumeroConta(numeroConta);
            String cpfCliente = payload.has("cpfCliente") ? payload.get("cpfCliente").asText()
                    : (payload.has("cpf") ? payload.get("cpf").asText() : "");
            String gerenteCpf = payload.has("gerenteCpf") ? payload.get("gerenteCpf").asText()
                    : (payload.has("cpfGerente") ? payload.get("cpfGerente").asText() : "");
            BigDecimal saldoInicial = payload.has("saldo") ? new BigDecimal(payload.get("saldo").asText()) : BigDecimal.ZERO;
            BigDecimal limite = payload.has("limite") ? new BigDecimal(payload.get("limite").asText()) : BigDecimal.ZERO;
            BigDecimal salario = payload.has("salario") ? new BigDecimal(payload.get("salario").asText()) : BigDecimal.ZERO;

            conta.setCpfCliente(cpfCliente);
            conta.setGerenteCpf(gerenteCpf);
            conta.setSaldo(saldoInicial.setScale(2, RoundingMode.HALF_UP));
            conta.setLimite(limite.setScale(2, RoundingMode.HALF_UP));
            conta.setSalario(salario.setScale(2, RoundingMode.HALF_UP));
            conta.setDataCriacao(dataHora);
            conta.setUltimaVersao(evento.getVersao());
            contaQueryRepository.save(conta);
            return;
        }

        Optional<ContaQuery> contaOpt = contaQueryRepository.findById(numeroConta);
        int ultimaVersaoProjetada = contaOpt.map(ContaQuery::getUltimaVersao).orElse(0);
        if (evento.getVersao() <= ultimaVersaoProjetada) {
            return; // reentrega (at-least-once): esta versão já foi aplicada
        }
        if (contaOpt.isEmpty() || evento.getVersao() > ultimaVersaoProjetada + 1) {
            if (!recebidoDeFora) {
                log.error("Event Store da conta {} sem a versão {}: projeção interrompida em v{}.",
                        numeroConta, ultimaVersaoProjetada + 1, ultimaVersaoProjetada);
                return;
            }
            // Lacuna: uma versão anterior não chegou (publicação perdida ou Criado ainda na fila). Aplicar
            // esta fora de ordem adiantaria ultima_versao e a anterior nunca mais seria projetada. Refaz a
            // partir do Event Store, a fonte da verdade, em ordem de versão (inclui este evento)
            log.warn("Lacuna na projeção da conta {}: projetada até v{}, recebida v{}. Sincronizando pelo Event Store.",
                    numeroConta, ultimaVersaoProjetada, evento.getVersao());
            sincronizarLadoQuerySeNecessario(numeroConta);
            return;
        }
        ContaQuery conta = contaOpt.get();

        if ("GERENTEALTERADO".equals(tipoNorm)) {
            if (payload.has("gerenteCpf")) {
                conta.setGerenteCpf(payload.get("gerenteCpf").asText());
            } else if (payload.has("cpfGerente")) {
                conta.setGerenteCpf(payload.get("cpfGerente").asText());
            }
            conta.setUltimaVersao(evento.getVersao());
            contaQueryRepository.save(conta);
            return;
        }

        MovimentacaoQuery mov = movimentacaoQueryRepository.existsById(evento.getId())
                ? null
                : construirMovimentacaoQuery(evento, conta.getCpfCliente());
        if (mov == null) {
            // Movimentação já gravada ou evento sem efeito no saldo: só avança a versão, para não abrir lacuna
            conta.setUltimaVersao(evento.getVersao());
            contaQueryRepository.save(conta);
            return;
        }

        movimentacaoQueryRepository.save(mov);

        if ("ENTRADA".equals(mov.getNatureza())) {
            conta.setSaldo(conta.getSaldo().add(mov.getValor()).setScale(2, RoundingMode.HALF_UP));
        } else {
            conta.setSaldo(conta.getSaldo().subtract(mov.getValor()).setScale(2, RoundingMode.HALF_UP));
        }
        conta.setUltimaVersao(evento.getVersao());
        contaQueryRepository.save(conta);
    }

    private void sincronizarLadoQuerySeNecessario(String numeroConta) {
        if (contaQueryRepository == null || movimentacaoQueryRepository == null) {
            return;
        }
        List<EventoConta> eventos = repository.findByObjetoIdOrderByVersaoAsc(numeroConta);
        if (eventos.isEmpty()) {
            return;
        }
        Optional<ContaQuery> contaOpt = contaQueryRepository.findById(numeroConta);
        int ultimaVersaoProjetada = contaOpt.map(ContaQuery::getUltimaVersao).orElse(0);
        int ultimaVersaoEventos = eventos.get(eventos.size() - 1).getVersao();

        if (ultimaVersaoProjetada < ultimaVersaoEventos) {
            for (EventoConta evento : eventos) {
                if (evento.getVersao() > ultimaVersaoProjetada) {
                    projetarEvento(evento, false);
                }
            }
        }
    }

    private MovimentacaoQuery construirMovimentacaoQuery(EventoConta evento, String cpfTitularConta) {
        String tipoNorm = normalizarTipoEvento(evento.getTipo());
        JsonNode payload = parseJsonNode(evento.getPayload());
        if (!payload.has("valor")) {
            return null;
        }

        BigDecimal valor = new BigDecimal(payload.get("valor").asText()).abs().setScale(2, RoundingMode.HALF_UP);
        LocalDateTime dataHora = extrairDataHoraEvento(evento, payload);

        MovimentacaoQuery mov = new MovimentacaoQuery();
        mov.setId(evento.getId() != null ? evento.getId() : UUID.randomUUID().toString());
        mov.setNumeroConta(evento.getObjetoId());
        mov.setDataHora(dataHora);
        mov.setValor(valor);
        mov.setVersao(evento.getVersao() != null ? evento.getVersao() : 1);

        switch (tipoNorm) {
            case "DEPOSITO" -> {
                mov.setTipo("DEPOSITO");
                mov.setNatureza("ENTRADA");
            }
            case "SAQUE" -> {
                mov.setTipo("SAQUE");
                mov.setNatureza("SAIDA");
            }
            case "TRANSFERENCIAORIGEM" -> {
                mov.setTipo("TRANSFERENCIA");
                mov.setNatureza("SAIDA");
                mov.setCpfOrigem(extrairTexto(payload, "cpfOrigem", cpfTitularConta));
                mov.setNomeOrigem(extrairTexto(payload, "nomeOrigem", null));
                mov.setCpfDestino(extrairTexto(payload, "cpfDestino", null));
                mov.setNomeDestino(extrairTexto(payload, "nomeDestino", null));
            }
            case "TRANSFERENCIADESTINO" -> {
                mov.setTipo("TRANSFERENCIA");
                mov.setNatureza("ENTRADA");
                mov.setCpfOrigem(extrairTexto(payload, "cpfOrigem", null));
                mov.setNomeOrigem(extrairTexto(payload, "nomeOrigem", null));
                mov.setCpfDestino(extrairTexto(payload, "cpfDestino", cpfTitularConta));
                mov.setNomeDestino(extrairTexto(payload, "nomeDestino", null));
            }
            default -> {
                return null;
            }
        }

        return mov;
    }

    private BigDecimal somarMovimentacoesComSinal(List<MovimentacaoQuery> movs) {
        BigDecimal soma = BigDecimal.ZERO;
        if (movs == null) {
            return soma;
        }
        for (MovimentacaoQuery mov : movs) {
            if ("ENTRADA".equals(mov.getNatureza())) {
                soma = soma.add(mov.getValor());
            } else {
                soma = soma.subtract(mov.getValor());
            }
        }
        return soma;
    }

    private ExtratoResponseDTO montarExtratoResponse(
            String numeroConta,
            BigDecimal saldoAbertura,
            BigDecimal saldoAtual,
            List<ItemExtratoDTO> itens
    ) {
        ExtratoResponseDTO response = new ExtratoResponseDTO(
                numeroConta,
                saldoAbertura.setScale(2, RoundingMode.HALF_UP).toString(),
                saldoAtual.setScale(2, RoundingMode.HALF_UP).toString(),
                itens
        );
        response.addLink("self", "http://localhost:8080/contas/" + numeroConta + "/extrato");
        response.addLink("conta", "http://localhost:8080/contas/" + numeroConta);
        response.addLink("deposito", "http://localhost:8080/contas/" + numeroConta + "/deposito");
        response.addLink("saque", "http://localhost:8080/contas/" + numeroConta + "/saque");
        response.addLink("transferencia", "http://localhost:8080/contas/" + numeroConta + "/transferencia");
        return response;
    }

    private LocalDate parseData(String valor, String nomeCampo) {
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Data de " + nomeCampo + " inválida: " + valor + ". Use o formato AAAA-MM-DD.");
        }
    }

    private LocalDateTime extrairDataHoraEvento(EventoConta evento, JsonNode payload) {
        if (evento.getTimestamp() != null) {
            return evento.getTimestamp();
        }
        if (payload != null && payload.has("dataHora") && !payload.get("dataHora").isNull()) {
            try {
                return LocalDateTime.parse(payload.get("dataHora").asText());
            } catch (Exception ignored) {
            }
        }
        return LocalDateTime.now();
    }

    private String extrairTexto(JsonNode node, String campo, String padrao) {
        if (node != null && node.has(campo) && !node.get(campo).isNull()) {
            String val = node.get(campo).asText();
            if (!val.isBlank()) {
                return val;
            }
        }
        return padrao;
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

    // O lado query só pode ver eventos que foram de fato gravados. Dentro de uma transação a publicação
    // espera o commit: se a R15 falhar na terceira conta, ou o depósito perder a disputa de versão, o
    // rollback desfaz os eventos e nada chega a ms.conta.events
    private void publicarEventoCqrs(EventoConta evento) {
        if (rabbitTemplate == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviarEventoCqrs(evento);
                }
            });
        } else {
            enviarEventoCqrs(evento);
        }
    }

    private void enviarEventoCqrs(EventoConta evento) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_CONTA_EVENTS, evento);
        } catch (Exception e) {
            // Sem retentativa aqui: a lacuna de versão é recuperada pelo Event Store no próximo evento ou consulta
            log.warn("Não foi possível publicar evento CQRS na fila {}: {}", RabbitMQConfig.FILA_CONTA_EVENTS, e.getMessage());
        }
    }

    private boolean formatoValorValido(String valor) {
        return valor != null && FORMATO_VALOR.matcher(valor).matches();
    }

    // Posse obrigatória (R4, R5, R6, R7): só o titular opera ou consulta o extrato da conta
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
