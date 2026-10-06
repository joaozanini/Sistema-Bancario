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
     * SAGA Passo 3: Identifica, dentre os gerentes ativos informados pelo Orquestrador,
     * o que possui menos clientes/contas atreladas (gerentes sem conta possuem 0 clientes).
     */
    @Transactional(readOnly = true)
    public String identificarGerenteComMenosClientes(List<String> gerentesAtivos) {
        if (gerentesAtivos == null || gerentesAtivos.isEmpty()) {
            throw new IllegalArgumentException("Lista de gerentes ativos não pode ser vazia.");
        }

        // Inicializa mapa de contagem para todos os gerentes ativos com 0
        Map<String, Long> contagemPorGerente = new HashMap<>();
        for (String cpf : gerentesAtivos) {
            if (cpf != null && !cpf.isBlank()) {
                contagemPorGerente.put(cpf.trim(), 0L);
            }
        }

        // Busca todos os eventos de criação e alteração de gerente no Event Store
        List<EventoConta> eventos = repository.findByTipoIn(List.of("Criado", "CRIADO", "GerenteAlterado", "GERENTEALTERADO"));
        
        // Mapeia cada conta para seu gerente atual
        Map<String, String> gerenteAtualPorConta = new HashMap<>();
        // Ordena por versão para garantir que o último evento de cada conta prevaleça
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

        // Contabiliza as contas atreladas a cada gerente ativo
        for (String gerenteCpf : gerenteAtualPorConta.values()) {
            if (contagemPorGerente.containsKey(gerenteCpf)) {
                contagemPorGerente.put(gerenteCpf, contagemPorGerente.get(gerenteCpf) + 1);
            }
        }

        // Seleciona o gerente com a menor quantidade de contas (em caso de empate, o primeiro)
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
     * SAGA Passo 6: Cria uma nova conta bancária com ID aleatório único de 4 dígitos
     * e vincula ao gerente responsável, persistindo o evento 'Criado' no Event Store.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> criarConta(String cpfCliente, String gerenteCpf, String salarioStr) {
        if (cpfCliente == null || cpfCliente.isBlank()) {
            throw new IllegalArgumentException("CPF do cliente é obrigatório para criação de conta.");
        }
        if (gerenteCpf == null || gerenteCpf.isBlank()) {
            throw new IllegalArgumentException("CPF do gerente é obrigatório para criação de conta.");
        }

        // Sorteia número de conta único de 4 dígitos com retry em caso de colisão
        String numeroConta;
        do {
            numeroConta = String.format("%04d", random.nextInt(10000));
        } while (repository.existsByObjetoId(numeroConta));

        LocalDateTime agora = LocalDateTime.now();
        BigDecimal salario = (salarioStr != null && !salarioStr.isBlank()) ? new BigDecimal(salarioStr) : BigDecimal.ZERO;
        // Limite padrão inicial proporcional ao salário se >= 2000 (ex: 50%), ou padrão
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

        // Publica evento para sincronização CQRS (ms.conta.events)
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
     * SAGA Compensação do Passo 6: Remove a conta criada no Event Store caso a SAGA falhe em passo posterior.
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

        // 1. Replay e validação da conta de origem
        List<EventoConta> eventosOrigem = repository.findByObjetoIdOrderByVersaoAsc(contaOrigem);
        if (eventosOrigem.isEmpty()) {
            throw new ContaNaoEncontradaException("Conta de origem não encontrada: " + contaOrigem);
        }

        EstadoConta estadoOrigem = reconstruirEstadoPorReplay(eventosOrigem);

        // Validação de posse da conta (se header X-User-CPF fornecido)
        if (xUserCpf != null && !xUserCpf.trim().isEmpty()) {
            if (estadoOrigem.getCpfCliente() != null && !xUserCpf.trim().equals(estadoOrigem.getCpfCliente().trim())) {
                throw new AcessoNegadoException("Usuário não autorizado a realizar operações nesta conta.");
            }
        }

        // Validação estrita de saldo via Replay
        if (estadoOrigem.getSaldo().compareTo(valorTransferencia) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente para realizar a transferência.");
        }

        // 2. Validação da existência da conta de destino
        List<EventoConta> eventosDestino = repository.findByObjetoIdOrderByVersaoAsc(contaDestino);
        if (eventosDestino.isEmpty()) {
            throw new ContaNaoEncontradaException("Conta de destino não encontrada: " + contaDestino);
        }
        EstadoConta estadoDestino = reconstruirEstadoPorReplay(eventosDestino);

        // 3. Determinar versões consecutivas para Optimistic Locking
        int proximaVersaoOrigem = eventosOrigem.get(eventosOrigem.size() - 1).getVersao() + 1;
        int proximaVersaoDestino = eventosDestino.get(eventosDestino.size() - 1).getVersao() + 1;

        LocalDateTime agora = LocalDateTime.now();
        String valorStr = valorTransferencia.setScale(2, RoundingMode.HALF_UP).toString();

        String cpfOrigem = dto.getCpfOrigem() != null ? dto.getCpfOrigem() : estadoOrigem.getCpfCliente();
        String cpfDestino = dto.getCpfDestino() != null ? dto.getCpfDestino() : estadoDestino.getCpfCliente();
        String nomeOrigem = dto.getNomeOrigem();
        String nomeDestino = dto.getNomeDestino();

        // 4. Montar Payloads JSON dos dois eventos
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

        // 5. Criar Eventos do Event Store
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

        // 6. Gravação Atômica na mesma transação local
        repository.save(eventoOrigem);
        repository.save(eventoDestino);

        // Publica eventos na fila CQRS
        publicarEventoCqrs(eventoOrigem);
        publicarEventoCqrs(eventoDestino);

        // 7. Montar resposta HATEOAS
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

    /**
     * Consulta os dados da conta calculados via Replay no Event Store.
     */
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

    /**
     * Replay dinâmico dos eventos da conta ordenados por versão para reconstrução de estado no Command side.
     */
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
                default -> {
                    // Outros tipos de evento que não afetam saldo diretamente
                }
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

    /**
     * Classe auxiliar interna que mantém o estado acumulado durante o Replay.
     */
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