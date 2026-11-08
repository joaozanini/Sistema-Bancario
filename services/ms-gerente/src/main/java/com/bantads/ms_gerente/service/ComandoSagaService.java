package com.bantads.ms_gerente.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bantads.ms_gerente.dto.MensagemSaga;

@Service
public class ComandoSagaService {

    private static final Logger log = LoggerFactory.getLogger(ComandoSagaService.class);

    // contrato com o AprovarClienteSaga do orquestrador (R9)
    public static final String LISTAR_ATIVOS = "gerente.listar-ativos";

    private static final DateTimeFormatter FORMATO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final GerenteService gerenteService;
    private final Clock clock;

    public ComandoSagaService(GerenteService gerenteService, Clock clock) {
        this.gerenteService = gerenteService;
        this.clock = clock;
    }

    public Optional<MensagemSaga> processar(MensagemSaga comando) {
        if (comando.sagaId() == null || comando.tipo() == null) {
            log.warn("Comando sem sagaId ou tipo descartado");
            return Optional.empty();
        }

        return switch (comando.tipo()) {
            case LISTAR_ATIVOS -> Optional.of(listarAtivos(comando));
            default -> Optional.of(MensagemSaga.falha(comando.sagaId(), comando.tipo(), agora(),
                    "Tipo de comando nao reconhecido pelo MS Gerente: " + comando.tipo()));
        };
    }

    // recebe {} e responde { gerentes: [cpf, ...] }; consulta sem efeito colateral, dispensa deduplicacao
    private MensagemSaga listarAtivos(MensagemSaga comando) {
        List<String> cpfs = gerenteService.listarCpfsAtivos();
        if (cpfs.isEmpty()) {
            log.warn("[{}] nenhum gerente ativo para receber o cliente: sagaId={}", comando.tipo(), comando.sagaId());
            return MensagemSaga.falha(comando.sagaId(), comando.tipo(), agora(), "Nenhum gerente ativo cadastrado");
        }
        log.info("[{}] {} gerente(s) ativo(s): sagaId={}", comando.tipo(), cpfs.size(), comando.sagaId());
        return MensagemSaga.sucesso(comando.sagaId(), comando.tipo(), agora(), Map.of("gerentes", cpfs));
    }

    private String agora() {
        return LocalDateTime.now(clock).format(FORMATO_TIMESTAMP);
    }
}
