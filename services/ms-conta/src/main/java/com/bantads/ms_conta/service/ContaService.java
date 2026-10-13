package com.bantads.ms_conta.service;

import com.bantads.ms_conta.config.RabbitMQConfig;
import com.bantads.ms_conta.dto.ContaDTO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ContaService {

    private static final Logger log = LoggerFactory.getLogger(ContaService.class);

    private final EventoContaRepository repository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final Random random = new Random();

    public ContaService(
            EventoContaRepository repository,
            ObjectMapper objectMapper,
            @Autowired(required = false) RabbitTemplate rabbitTemplate
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
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
     * Regra: Pega o gerente ativo existente que possui mais contas.
     * Se houver empate no número de contas, pega o gerente que possui menor saldo total.
     * A conta transferida é sempre a conta de MENOR saldo desse gerente escolhido.
     * Se for o primeiro gerente ou se todos os gerentes ativos tiverem no máximo 1 conta,
     * nenhuma conta é transferida (retorna null / "sem conta").
     */
    @Transactional(readOnly = true)
    public Map<String, Object> identificarContaParaNovoGerente(List<String> gerentesAtivos) {
        if (gerentesAtivos == null || gerentesAtivos.isEmpty()) {
            return null; // sem conta a transferir
        }

        Map<String, String> gerenteAtualPorConta = mapearGerenteAtualPorConta();

        // Agrupa as contas por gerente ativo
        Map<String, List<String>> contasPorGerente = new HashMap<>();
        for (String cpf : gerentesAtivos) {
            if (cpf != null && !cpf.isBlank()) {
                contasPorGerente.put(cpf.trim(), new ArrayList<>());
            }
        }

        for (Map.Entry<String, String> entry : gerenteAtualPorConta.entrySet()) {
            String conta = entry.getKey();
            String gerente = entry.getValue();
            if (contasPorGerente.containsKey(gerente)) {
                contasPorGerente.get(gerente).add(conta);
            }
        }

        // Verifica o número máximo de contas entre os gerentes ativos existentes
        int maxContas = contasPorGerente.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        // Se nenhum gerente tem mais de 1 conta (ou seja, todos têm 0 ou 1 conta), NENHUMA conta é transferida
        if (maxContas <= 1) {
            log.info("Nenhuma conta a transferir para o novo gerente: todos os gerentes possuem no máximo 1 conta.");
            return null;
        }

        // Filtra gerentes empatados com maxContas
        List<String> gerentesComMaisContas = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : contasPorGerente.entrySet()) {
            if (entry.getValue().size() == maxContas) {
                gerentesComMaisContas.add(entry.getKey());
            }
        }

        // Calcula saldo de cada gerente empatado (soma dos saldos das contas dele)
        // e identifica as contas com seus respectivos saldos via Replay
        String gerenteEscolhido = null;
        BigDecimal menorSaldoTotalGerente = null;

        Map<String, BigDecimal> saldoPorConta = new HashMap<>();
        Map<String, String> titularPorConta = new HashMap<>();

        for (String gerenteCpf : gerentesComMaisContas) {
            BigDecimal saldoTotalDoGerente = BigDecimal.ZERO;
            for (String numeroConta : contasPorGerente.get(gerenteCpf)) {
                List<EventoConta> eventos = repository.findByObjetoIdOrderByVersaoAsc(numeroConta);
                EstadoConta estado = reconstruirEstadoPorReplay(eventos);
                saldoPorConta.put(numeroConta, estado.getSaldo());
                titularPorConta.put(numeroConta, estado.getCpfCliente());
                saldoTotalDoGerente = saldoTotalDoGerente.add(estado.getSaldo());
            }

            if (menorSaldoTotalGerente == null || saldoTotalDoGerente.compareTo(menorSaldoTotalGerente) < 0) {
                menorSaldoTotalGerente = saldoTotalDoGerente;
                gerenteEscolhido = gerenteCpf;
            }
        }

        if (gerenteEscolhido == null) {
            return null;
        }

        // A conta transferida é sempre a conta de MENOR saldo do gerente escolhido
        List<String> contasDoEscolhido = contasPorGerente.get(gerenteEscolhido);
        String contaMenorSaldo = null;
        BigDecimal menorSaldoConta = null;

        for (String conta : contasDoEscolhido) {
            BigDecimal saldo = saldoPorConta.get(conta);
            if (saldo == null) {
                List<EventoConta> eventos = repository.findByObjetoIdOrderByVersaoAsc(conta);
                EstadoConta estado = reconstruirEstadoPorReplay(eventos);
                saldo = estado.getSaldo();
                saldoPorConta.put(conta, saldo);
                titularPorConta.put(conta, estado.getCpfCliente());
            }
            if (menorSaldoConta == null || saldo.compareTo(menorSaldoConta) < 0) {
                menorSaldoConta = saldo;
                contaMenorSaldo = conta;
            }
        }

        if (contaMenorSaldo == null) {
            return null;
        }

        String cpfCliente = titularPorConta.get(contaMenorSaldo);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("numeroConta", contaMenorSaldo);
        resultado.put("cpfCliente", cpfCliente);
        resultado.put("gerenteAntigoCpf", gerenteEscolhido);
        resultado.put("saldo", menorSaldoConta != null ? menorSaldoConta.toString() : "0.00");

        log.info("Conta identificada para transferência: {} (cliente: {}, gerente antigo: {})",
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
     * Requisito R6: Transferência Atômica entre contas.
     */
    @Transactional(rollbackFor = Exception.class)
    public TransferenciaResponseDTO transferir(String contaOrigem, TransferenciaRequestDTO dto, String xUserCpf) {
        if (contaOrigem == null || contaOrigem.trim().isEmpty()) {
            throw new TransferenciaInvalidaException("Conta de origem é obrigatória.");
        }
        String contaDestino = dto.getContaDestino();
        if (contaDestino == null || contaDestino.trim().isEmpty()) {
            throw new TransferenciaInvalidaException("Conta de destino é obrigatória.");
        }
        if (contaOrigem.trim().equals(contaDestino.trim())) {
            throw new TransferenciaInvalidaException("A conta de destino não pode ser igual à conta de origem.");
        }

        BigDecimal valorTransferencia;
        try {
            valorTransferencia = new BigDecimal(dto.getValor());
        } catch (Exception e) {
            throw new TransferenciaInvalidaException("Valor da transferência inválido.");
        }

        if (valorTransferencia.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransferenciaInvalidaException("O valor da transferência deve ser maior que zero.");
        }

        List<EventoConta> eventosOrigem = repository.findByObjetoIdOrderByVersaoAsc(contaOrigem);
        if (eventosOrigem.isEmpty()) {
            throw new ContaNaoEncontradaException("Conta de origem não encontrada: " + contaOrigem);
        }

        EstadoConta estadoOrigem = reconstruirEstadoPorReplay(eventosOrigem);

        if (xUserCpf != null && !xUserCpf.trim().isEmpty()) {
            if (estadoOrigem.getCpfCliente() != null && !xUserCpf.trim().equals(estadoOrigem.getCpfCliente().trim())) {
                throw new AcessoNegadoException("Usuário não autorizado a realizar operações nesta conta.");
            }
        }

        if (estadoOrigem.getSaldo().compareTo(valorTransferencia) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente para realizar a transferência.");
        }

        List<EventoConta> eventosDestino = repository.findByObjetoIdOrderByVersaoAsc(contaDestino);
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
        payloadOrigemMap.put("contaOrigem", contaOrigem);
        payloadOrigemMap.put("contaDestino", contaDestino);
        payloadOrigemMap.put("cpfOrigem", cpfOrigem);
        payloadOrigemMap.put("nomeOrigem", nomeOrigem);
        payloadOrigemMap.put("cpfDestino", cpfDestino);
        payloadOrigemMap.put("nomeDestino", nomeDestino);

        Map<String, Object> payloadDestinoMap = new HashMap<>(payloadOrigemMap);

        String payloadOrigemJson = converterParaJson(payloadOrigemMap);
        String payloadDestinoJson = converterParaJson(payloadDestinoMap);

        EventoConta eventoOrigem = new EventoConta();
        eventoOrigem.setId(UUID.randomUUID().toString());
        eventoOrigem.setObjetoId(contaOrigem);
        eventoOrigem.setTipo("TransferênciaOrigem");
        eventoOrigem.setPayload(payloadOrigemJson);
        eventoOrigem.setVersao(proximaVersaoOrigem);
        eventoOrigem.setTimestamp(agora);

        EventoConta eventoDestino = new EventoConta();
        eventoDestino.setId(UUID.randomUUID().toString());
        eventoDestino.setObjetoId(contaDestino);
        eventoDestino.setTipo("TransferênciaDestino");
        eventoDestino.setPayload(payloadDestinoJson);
        eventoDestino.setVersao(proximaVersaoDestino);
        eventoDestino.setTimestamp(agora);

        repository.save(eventoOrigem);
        repository.save(eventoDestino);

        publicarEventoCqrs(eventoOrigem);
        publicarEventoCqrs(eventoDestino);

        TransferenciaResponseDTO resposta = new TransferenciaResponseDTO();
        resposta.setIdOrigem(eventoOrigem.getId());
        resposta.setIdDestino(eventoDestino.getId());
        resposta.setContaOrigem(contaOrigem);
        resposta.setContaDestino(contaDestino);
        resposta.setValor(valorStr);
        resposta.setDataHora(agora);
        resposta.setMensagem("Transferência realizada com sucesso.");

        resposta.addLink("self", "http://localhost:8080/contas/" + contaOrigem + "/transferencia");
        resposta.addLink("deposito", "http://localhost:8080/contas/" + contaOrigem + "/deposito");
        resposta.addLink("saque", "http://localhost:8080/contas/" + contaOrigem + "/saque");
        resposta.addLink("extrato", "http://localhost:8080/contas/" + contaOrigem + "/extrato");

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