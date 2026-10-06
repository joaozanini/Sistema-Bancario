package com.bantads.ms_conta.consumer;

import com.bantads.ms_conta.config.RabbitMQConfig;
import com.bantads.ms_conta.dto.MensagemSagaDTO;
import com.bantads.ms_conta.dto.RespostaSagaDTO;
import com.bantads.ms_conta.model.SagaIdempotencia;
import com.bantads.ms_conta.repository.SagaIdempotenciaRepository;
import com.bantads.ms_conta.service.ContaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class ContaCommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(ContaCommandConsumer.class);

    private final ContaService contaService;
    private final SagaIdempotenciaRepository idempotenciaRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public ContaCommandConsumer(
            ContaService contaService,
            SagaIdempotenciaRepository idempotenciaRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.contaService = contaService;
        this.idempotenciaRepository = idempotenciaRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.FILA_CONTA_CMD)
    public void processarComandoSaga(MensagemSagaDTO mensagem) {
        if (mensagem == null) {
            log.warn("Mensagem recebida nula na fila {}", RabbitMQConfig.FILA_CONTA_CMD);
            return;
        }

        String sagaId = mensagem.getSagaId();
        String tipo = mensagem.getTipo();
        Map<String, Object> payload = mensagem.getPayload() != null ? mensagem.getPayload() : Collections.emptyMap();

        log.info("Processando comando SAGA - sagaId: {}, tipo: {}", sagaId, tipo);

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
                log.warn("Não foi possível persistir registro de idempotência: {}", e.getMessage());
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

        // 1. Identificar Gerente com Menos Clientes (Passo 3 da SAGA Aprovar Cliente)
        if (tipoNorm.contains("identificar-gerente") || tipoNorm.contains("identificar_gerente") || tipoNorm.equals("conta.identificar-gerente")) {
            List<String> gerentes = extrairListaGerentes(payload);
            String gerenteEscolhido = contaService.identificarGerenteComMenosClientes(gerentes);

            Map<String, Object> respostaPayload = new HashMap<>();
            respostaPayload.put("cpfGerente", gerenteEscolhido);
            respostaPayload.put("cpf", gerenteEscolhido);
            respostaPayload.put("gerenteCpf", gerenteEscolhido);

            return RespostaSagaDTO.sucesso(sagaId, tipo, respostaPayload);
        }

        // 2. Criar Conta (Passo 6 da SAGA Aprovar Cliente)
        if (tipoNorm.contains("criar-conta") || tipoNorm.contains("criar_conta") || tipoNorm.equals("conta.criar") || tipoNorm.equals("ms.conta.criar")) {
            String cpfCliente = extrairString(payload, "cpfCliente", "cpf");
            String gerenteCpf = extrairString(payload, "gerenteCpf", "cpfGerente");
            String salarioStr = extrairString(payload, "salario", "salarioCliente");

            Map<String, Object> resultado = contaService.criarConta(cpfCliente, gerenteCpf, salarioStr);
            return RespostaSagaDTO.sucesso(sagaId, tipo, resultado);
        }

        // 3. Compensação de Criar Conta (Remover Conta)
        if (tipoNorm.contains("compensar") || tipoNorm.contains("remover-conta") || tipoNorm.contains("remover_conta")) {
            String numeroConta = extrairString(payload, "numeroConta", "objetoId", "conta");
            String cpfCliente = extrairString(payload, "cpfCliente", "cpf");

            contaService.compensarCriacaoConta(numeroConta, cpfCliente);

            Map<String, Object> respostaPayload = new HashMap<>();
            respostaPayload.put("mensagem", "Compensação de criação de conta efetuada.");
            return RespostaSagaDTO.sucesso(sagaId, tipo, respostaPayload);
        }

        throw new IllegalArgumentException("Tipo de comando não reconhecido pelo MS Conta: " + tipo);
    }

    @SuppressWarnings("unchecked")
    private List<String> extrairListaGerentes(Map<String, Object> payload) {
        List<String> listaCpfs = new ArrayList<>();
        if (payload.containsKey("gerentes")) {
            Object obj = payload.get("gerentes");
            if (obj instanceof List<?> l) {
                for (Object item : l) {
                    if (item instanceof String s) {
                        listaCpfs.add(s);
                    } else if (item instanceof Map<?, ?> m) {
                        if (m.containsKey("cpf")) {
                            listaCpfs.add(String.valueOf(m.get("cpf")));
                        }
                    }
                }
            }
        } else if (payload.containsKey("gerentesAtivos")) {
            Object obj = payload.get("gerentesAtivos");
            if (obj instanceof List<?> l) {
                for (Object item : l) {
                    if (item instanceof String s) listaCpfs.add(s);
                }
            }
        } else if (payload.containsKey("cpfs")) {
            Object obj = payload.get("cpfs");
            if (obj instanceof List<?> l) {
                for (Object item : l) {
                    if (item instanceof String s) listaCpfs.add(s);
                }
            }
        }
        return listaCpfs;
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
