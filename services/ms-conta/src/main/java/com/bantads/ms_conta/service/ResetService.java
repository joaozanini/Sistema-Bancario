package com.bantads.ms_conta.service;

import com.bantads.ms_conta.model.EventoConta;
import com.bantads.ms_conta.repository.ContaQueryRepository;
import com.bantads.ms_conta.repository.EventoContaRepository;
import com.bantads.ms_conta.repository.MovimentacaoQueryRepository;
import com.bantads.ms_conta.repository.SagaIdempotenciaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ResetService {

    private final EventoContaRepository repository;
    private final ContaQueryRepository contaQueryRepository;
    private final MovimentacaoQueryRepository movimentacaoQueryRepository;
    private final SagaIdempotenciaRepository idempotenciaRepository;
    private final ContaService contaService;
    private final ObjectMapper objectMapper;

    public ResetService(
            EventoContaRepository repository,
            ContaQueryRepository contaQueryRepository,
            MovimentacaoQueryRepository movimentacaoQueryRepository,
            SagaIdempotenciaRepository idempotenciaRepository,
            ContaService contaService,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.contaQueryRepository = contaQueryRepository;
        this.movimentacaoQueryRepository = movimentacaoQueryRepository;
        this.idempotenciaRepository = idempotenciaRepository;
        this.contaService = contaService;
        this.objectMapper = objectMapper;
    }

    // Contas e movimentacoes pre-cadastradas (enunciado, secao 4): o replay dos eventos fecha os saldos da tabela
    @Transactional
    public void reset() {
        idempotenciaRepository.deleteAllInBatch();
        movimentacaoQueryRepository.deleteAllInBatch();
        contaQueryRepository.deleteAllInBatch();
        repository.deleteAllInBatch();

        List<EventoConta> eventos = new ArrayList<>();
        Map<String, Integer> versoes = new HashMap<>();

        criado(eventos, versoes, "1291", "12912861012", "98574307084", "10000.00", "5000.00", "2000-01-01T00:00:00");
        criado(eventos, versoes, "0950", "09506382000", "64065268052", "20000.00", "10000.00", "1990-10-10T00:00:00");
        criado(eventos, versoes, "8573", "85733854057", "23862179060", "3000.00", "1500.00", "2012-12-12T00:00:00");
        criado(eventos, versoes, "5887", "58872160006", "98574307084", "500.00", "0.00", "2022-02-22T00:00:00");
        criado(eventos, versoes, "7617", "76179646090", "64065268052", "1500.00", "0.00", "2025-01-01T00:00:00");

        movimentacao(eventos, versoes, "1291", "Depósito", "1000.00", "2020-01-01T10:00:00");
        movimentacao(eventos, versoes, "1291", "Depósito", "900.00", "2020-01-01T11:00:00");
        movimentacao(eventos, versoes, "1291", "Saque", "550.00", "2020-01-01T12:00:00");
        movimentacao(eventos, versoes, "1291", "Saque", "350.00", "2020-01-01T13:00:00");
        movimentacao(eventos, versoes, "1291", "Depósito", "2000.00", "2020-01-10T15:00:00");
        movimentacao(eventos, versoes, "1291", "Saque", "500.00", "2020-01-15T08:00:00");
        transferencia(eventos, versoes, "1291", "12912861012", "Catharyna", "0950", "09506382000", "Cleuddônio", "1700.00", "2020-01-20T12:00:00");
        movimentacao(eventos, versoes, "0950", "Depósito", "1000.00", "2025-01-01T12:00:00");
        movimentacao(eventos, versoes, "0950", "Depósito", "5000.00", "2025-01-02T10:00:00");
        movimentacao(eventos, versoes, "0950", "Saque", "200.00", "2025-01-10T10:00:00");
        movimentacao(eventos, versoes, "0950", "Depósito", "7000.00", "2025-02-05T10:00:00");
        movimentacao(eventos, versoes, "0950", "Saque", "4500.00", "2025-03-06T11:00:00");
        movimentacao(eventos, versoes, "8573", "Depósito", "1000.00", "2025-05-05T10:00:00");
        movimentacao(eventos, versoes, "8573", "Saque", "800.00", "2025-05-06T10:00:00");
        movimentacao(eventos, versoes, "5887", "Depósito", "150000.00", "2025-06-01T10:00:00");
        movimentacao(eventos, versoes, "7617", "Depósito", "1500.00", "2025-07-01T10:00:00");

        repository.saveAll(eventos);
        eventos.forEach(contaService::projetarEventoNoLadoQuery);
    }

    private void criado(List<EventoConta> eventos, Map<String, Integer> versoes, String numeroConta, String cpfCliente,
                        String gerenteCpf, String salario, String limite, String dataCriacao) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("cpfCliente", cpfCliente);
        payload.put("gerenteCpf", gerenteCpf);
        payload.put("salario", salario);
        payload.put("limite", limite);
        payload.put("dataCriacao", dataCriacao);
        eventos.add(evento(versoes, numeroConta, "Criado", payload, dataCriacao));
    }

    private void movimentacao(List<EventoConta> eventos, Map<String, Integer> versoes, String numeroConta, String tipo,
                              String valor, String dataHora) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("valor", valor);
        payload.put("numeroConta", numeroConta);
        payload.put("dataHora", dataHora);
        eventos.add(evento(versoes, numeroConta, tipo, payload, dataHora));
    }

    private void transferencia(List<EventoConta> eventos, Map<String, Integer> versoes, String contaOrigem, String cpfOrigem,
                               String nomeOrigem, String contaDestino, String cpfDestino, String nomeDestino,
                               String valor, String dataHora) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("valor", valor);
        payload.put("contaOrigem", contaOrigem);
        payload.put("contaDestino", contaDestino);
        payload.put("cpfOrigem", cpfOrigem);
        payload.put("nomeOrigem", nomeOrigem);
        payload.put("cpfDestino", cpfDestino);
        payload.put("nomeDestino", nomeDestino);
        eventos.add(evento(versoes, contaOrigem, "TransferênciaOrigem", payload, dataHora));
        eventos.add(evento(versoes, contaDestino, "TransferênciaDestino", payload, dataHora));
    }

    private EventoConta evento(Map<String, Integer> versoes, String numeroConta, String tipo, Map<String, Object> payload, String dataHora) {
        int versao = versoes.merge(numeroConta, 1, Integer::sum);
        EventoConta evento = new EventoConta();
        evento.setId(UUID.randomUUID().toString());
        evento.setObjetoId(numeroConta);
        evento.setTipo(tipo);
        evento.setPayload(converterParaJson(payload));
        evento.setVersao(versao);
        evento.setTimestamp(LocalDateTime.parse(dataHora));
        return evento;
    }

    private String converterParaJson(Object objeto) {
        try {
            return objectMapper.writeValueAsString(objeto);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao serializar payload do evento.", e);
        }
    }
}
