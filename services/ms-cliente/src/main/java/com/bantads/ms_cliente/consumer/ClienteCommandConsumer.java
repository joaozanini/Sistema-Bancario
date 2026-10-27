package com.bantads.ms_cliente.consumer;

import com.bantads.ms_cliente.config.RabbitMQConfig;
import com.bantads.ms_cliente.dto.MensagemSagaDTO;
import com.bantads.ms_cliente.dto.RespostaSagaDTO;
import com.bantads.ms_cliente.model.SagaIdempotencia;
import com.bantads.ms_cliente.repository.SagaIdempotenciaRepository;
import com.bantads.ms_cliente.service.ClienteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class ClienteCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClienteCommandConsumer.class);

    private final ClienteService clienteService;
    private final SagaIdempotenciaRepository idempotenciaRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public ClienteCommandConsumer(
            ClienteService clienteService,
            SagaIdempotenciaRepository idempotenciaRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.clienteService = clienteService;
        this.idempotenciaRepository = idempotenciaRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.FILA_CLIENTE_CMD)
    public void processarComandoSaga(MensagemSagaDTO mensagem) {
        if (mensagem == null) {
            log.warn("Mensagem recebida nula na fila {}", RabbitMQConfig.FILA_CLIENTE_CMD);
            return;
        }

        String sagaId = mensagem.getSagaId();
        String tipo = mensagem.getTipo();
        Map<String, Object> payload = mensagem.getPayload() != null ? mensagem.getPayload() : Collections.emptyMap();

        log.info("Processando comando SAGA no MS Cliente - sagaId: {}, tipo: {}", sagaId, tipo);

        // Deduplicação / Idempotência
        if (sagaId != null && tipo != null) {
            Optional<SagaIdempotencia> existente = idempotenciaRepository.findBySagaIdAndTipo(sagaId, tipo);
            if (existente.isPresent()) {
                log.warn("Comando duplicado detectado para sagaId: {} e tipo: {}. Reenviando resposta armazenada.", sagaId, tipo);
                try {
                    if (existente.get().getResposta() != null) {
                        RespostaSagaDTO respostaSalva = objectMapper.readValue(existente.get().getResposta(), RespostaSagaDTO.class);
                        rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_ORQUESTRADOR_REPLY, respostaSalva);
                    }
                } catch (Exception e) {
                    log.error("Erro ao reenviar resposta salva para sagaId {}: {}", sagaId, e.getMessage());
                }
                return;
            }
        }

        RespostaSagaDTO resposta;
        try {
            resposta = executarComando(sagaId, tipo, payload);
        } catch (Exception ex) {
            log.error("Falha ao executar comando SAGA {} (sagaId {}): {}", tipo, sagaId, ex.getMessage(), ex);
            resposta = RespostaSagaDTO.falha(sagaId, tipo, ex.getMessage());
        }

        // Salva resposta para garantir idempotência futura
        if (sagaId != null && tipo != null) {
            try {
                String respostaJson = objectMapper.writeValueAsString(resposta);
                SagaIdempotencia idempotencia = new SagaIdempotencia(
                        UUID.randomUUID().toString(),
                        sagaId,
                        tipo,
                        respostaJson,
                        LocalDateTime.now()
                );
                idempotenciaRepository.save(idempotencia);
            } catch (Exception e) {
                log.warn("Não foi possível persistir registro de idempotência no MS Cliente: {}", e.getMessage());
            }
        }

        // Envia resposta para o Orquestrador
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_ORQUESTRADOR_REPLY, resposta);
            log.info("Resposta SAGA enviada com sucesso para orquestrador.reply: sagaId={}, status={}", sagaId, resposta.getStatus());
        } catch (Exception e) {
            log.error("Erro ao publicar resposta na fila {}: {}", RabbitMQConfig.FILA_ORQUESTRADOR_REPLY, e.getMessage());
            throw new RuntimeException("Falha ao enviar resposta para o orquestrador", e);
        }
    }

    private RespostaSagaDTO executarComando(String sagaId, String tipo, Map<String, Object> payload) {
        String tipoNorm = tipo != null ? tipo.toLowerCase().replace("_", "-").trim() : "";

        // 1. SAGA Remover Gerente (R15): Obter dados dos clientes transferidos
        if (tipoNorm.contains("clientes-transferidos")
                || tipoNorm.contains("obter-clientes")
                || tipoNorm.contains("buscar-clientes")
                || tipoNorm.contains("dados-clientes")
                || tipoNorm.contains("listar-por-cpf")
                || tipoNorm.contains("listar-clientes")
                || tipoNorm.contains("remover-gerente")) {

            List<String> cpfs = extrairCpfsClientes(payload);
            List<Map<String, Object>> dadosClientes = clienteService.obterDadosClientesTransferidos(cpfs);

            Map<String, Object> respostaPayload = new LinkedHashMap<>();
            respostaPayload.put("clientes", dadosClientes);
            respostaPayload.put("clientesTransferidos", dadosClientes);
            return RespostaSagaDTO.sucesso(sagaId, tipo, respostaPayload);
        }

        // 2. Consulta de um único cliente (ex.: R13 / R15 quando enviado individualmente)
        if (tipoNorm.contains("obter-cliente") || tipoNorm.contains("buscar-cliente") || tipoNorm.contains("consultar-cliente")) {
            List<String> cpfs = extrairCpfsClientes(payload);
            if (cpfs.size() > 1 || payload.containsKey("cpfs") || payload.containsKey("clientesTransferidos") || payload.containsKey("contasTransferidas")) {
                List<Map<String, Object>> dadosClientes = clienteService.obterDadosClientesTransferidos(cpfs);
                Map<String, Object> respostaPayload = new LinkedHashMap<>();
                respostaPayload.put("clientes", dadosClientes);
                respostaPayload.put("clientesTransferidos", dadosClientes);
                return RespostaSagaDTO.sucesso(sagaId, tipo, respostaPayload);
            }
            String cpf = extrairString(payload, "cpfCliente", "cpf", "clienteCpf");
            Map<String, Object> dadosCliente = clienteService.obterDadosClientePorCpf(cpf);
            return RespostaSagaDTO.sucesso(sagaId, tipo, dadosCliente);
        }

        // 3. SAGA Aprovar Cliente (R9 - Compensação Passo 1)
        if (tipoNorm.contains("aprovar-solicitacao.compensar") || tipoNorm.contains("compensar-aprovar-solicitacao")) {
            String cpf = extrairString(payload, "cpf", "cpfCliente");
            String statusSolicitacao = extrairString(payload, "statusSolicitacao", "status");
            String motivo = extrairString(payload, "motivo", "motivoRejeicao");
            clienteService.compensarAprovacaoSolicitacao(cpf, statusSolicitacao, motivo);
            return RespostaSagaDTO.sucesso(sagaId, tipo, Map.of("mensagem", "Compensação de solicitação efetuada."));
        }

        // 4. SAGA Aprovar Cliente (R9 - Passo 1)
        if (tipoNorm.contains("aprovar-solicitacao")) {
            String cpf = extrairString(payload, "cpf", "cpfCliente");
            Map<String, Object> resultado = clienteService.aprovarSolicitacao(cpf);
            return RespostaSagaDTO.sucesso(sagaId, tipo, resultado);
        }

        // 5. SAGA Aprovar Cliente (R9 - Compensação Passo 4)
        if (tipoNorm.contains("criar-cliente.compensar") || tipoNorm.contains("compensar-criar-cliente") || tipoNorm.contains("remover-cliente")) {
            String cpf = extrairString(payload, "cpf", "cpfCliente");
            clienteService.compensarCriacaoCliente(cpf);
            return RespostaSagaDTO.sucesso(sagaId, tipo, Map.of("mensagem", "Compensação de criação de cliente efetuada."));
        }

        // 6. SAGA Aprovar Cliente (R9 - Passo 4)
        if (tipoNorm.contains("criar-cliente")) {
            String cpf = extrairString(payload, "cpf", "cpfCliente");
            Map<String, Object> resultado = clienteService.criarClienteDaSolicitacao(cpf);
            return RespostaSagaDTO.sucesso(sagaId, tipo, resultado);
        }

        throw new IllegalArgumentException("Tipo de comando não reconhecido pelo MS Cliente: " + tipo);
    }

    private List<String> extrairCpfsClientes(Map<String, Object> payload) {
        List<String> cpfs = new ArrayList<>();
        String[] chavesLista = {
                "cpfs", "cpfsClientes", "cpfClientes", "clientesTransferidos",
                "contasTransferidas", "transferencias", "contas", "clientes"
        };

        for (String chave : chavesLista) {
            if (payload.containsKey(chave) && payload.get(chave) instanceof List<?> lista) {
                for (Object item : lista) {
                    if (item instanceof String s && !s.isBlank()) {
                        cpfs.add(s.trim());
                    } else if (item instanceof Map<?, ?> m) {
                        Object cpfObj = m.get("cpfCliente");
                        if (cpfObj == null) cpfObj = m.get("cpf");
                        if (cpfObj == null) cpfObj = m.get("clienteCpf");
                        if (cpfObj != null && !String.valueOf(cpfObj).isBlank()) {
                            cpfs.add(String.valueOf(cpfObj).trim());
                        }
                    }
                }
            }
        }

        if (cpfs.isEmpty()) {
            String cpfUnico = extrairString(payload, "cpfCliente", "clienteCpf", "cpf");
            if (cpfUnico != null && !cpfUnico.isBlank()) {
                cpfs.add(cpfUnico);
            }
        }

        return cpfs;
    }

    private String extrairString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key) && payload.get(key) != null) {
                return String.valueOf(payload.get(key)).trim();
            }
        }
        return null;
    }
}
