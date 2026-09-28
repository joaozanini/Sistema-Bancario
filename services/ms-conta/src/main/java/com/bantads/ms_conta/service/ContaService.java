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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ContaService {

    private final EventoContaRepository repository;
    private final ObjectMapper objectMapper;

    public ContaService(EventoContaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Requisito R6: Transferência Atômica entre contas.
     * Grava dois eventos (TransferênciaOrigem e TransferênciaDestino) de forma atômica
     * em uma única transação local (@Transactional) no Event Store.
     * A validação de saldo é feita estritamente através do cálculo dinâmico (replay)
     * dos eventos anteriores no Command side.
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