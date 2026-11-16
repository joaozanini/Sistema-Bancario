package com.bantads.ms_gerente.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bantads.ms_gerente.dto.GerenteDTO;
import com.bantads.ms_gerente.dto.MensagemSaga;
import com.bantads.ms_gerente.dto.StatusResposta;
import com.bantads.ms_gerente.exception.CpfJaCadastradoException;
import com.bantads.ms_gerente.exception.EmailJaCadastradoException;
import com.bantads.ms_gerente.exception.GerenteNaoEncontradoException;
import com.bantads.ms_gerente.exception.UltimoGerenteAtivoException;
import com.bantads.ms_gerente.model.Gerente;
import com.bantads.ms_gerente.model.SagaIdempotencia;
import com.bantads.ms_gerente.repository.SagaIdempotenciaRepository;

import tools.jackson.databind.json.JsonMapper;

@Service
public class ComandoSagaService {

    private static final Logger log = LoggerFactory.getLogger(ComandoSagaService.class);

    // Comando da SAGA Aprovar Cliente (R9)
    public static final String LISTAR_ATIVOS = "gerente.listar-ativos";
    // Comandos da SAGA Inserir Gerente (R13)
    public static final String INSERIR = "gerente.inserir";
    public static final String INSERIR_COMPENSAR = "gerente.inserir.compensar";
    // Comandos da SAGA Remover Gerente (R15)
    public static final String INATIVAR = "gerente.inativar";
    public static final String INATIVAR_COMPENSAR = "gerente.inativar.compensar";

    private static final DateTimeFormatter FORMATO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final GerenteService gerenteService;
    private final SagaIdempotenciaRepository idempotenciaRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ComandoSagaService(GerenteService gerenteService, SagaIdempotenciaRepository idempotenciaRepository,
            JsonMapper jsonMapper, Clock clock) {
        this.gerenteService = gerenteService;
        this.idempotenciaRepository = idempotenciaRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public Optional<MensagemSaga> processar(MensagemSaga comando) {
        if (comando.sagaId() == null || comando.tipo() == null) {
            log.warn("Comando sem sagaId ou tipo descartado");
            return Optional.empty();
        }

        return switch (comando.tipo()) {
            case LISTAR_ATIVOS -> Optional.of(listarAtivos(comando));
            case INSERIR -> Optional.of(umaVez(comando, c -> seNaoCompensado(c, INSERIR_COMPENSAR, this::inserir)));
            case INSERIR_COMPENSAR -> Optional.of(umaVez(comando, this::inserirCompensar));
            case INATIVAR -> Optional.of(umaVez(comando, c -> seNaoCompensado(c, INATIVAR_COMPENSAR, this::inativar)));
            case INATIVAR_COMPENSAR -> Optional.of(umaVez(comando, this::inativarCompensar));
            default -> Optional.of(MensagemSaga.falha(comando.sagaId(), comando.tipo(), agora(),
                    "Tipo de comando nao reconhecido pelo MS Gerente: " + comando.tipo()));
        };
    }

    // Responde a lista de CPFs dos gerentes
    private MensagemSaga listarAtivos(MensagemSaga comando) {
        List<String> cpfs = gerenteService.listarCpfsAtivos();
        if (cpfs.isEmpty()) {
            log.warn("[{}] nenhum gerente ativo para receber o cliente: sagaId={}", comando.tipo(), comando.sagaId());
            return MensagemSaga.falha(comando.sagaId(), comando.tipo(), agora(), "Nenhum gerente ativo cadastrado");
        }
        log.info("[{}] {} gerente(s) ativo(s): sagaId={}", comando.tipo(), cpfs.size(), comando.sagaId());
        return MensagemSaga.sucesso(comando.sagaId(), comando.tipo(), agora(), Map.of("gerentes", cpfs));
    }

    // Devolve a resposta guardada se o comando já foi executado
    private MensagemSaga umaVez(MensagemSaga comando, Function<MensagemSaga, MensagemSaga> passo) {
        Optional<SagaIdempotencia> registrado = idempotenciaRepository.findBySagaIdAndTipo(comando.sagaId(), comando.tipo());
        if (registrado.isPresent()) {
            log.warn("[{}] comando repetido, reenviando resposta guardada: sagaId={}", comando.tipo(), comando.sagaId());
            return jsonMapper.readValue(registrado.get().getResposta(), MensagemSaga.class);
        }
        MensagemSaga resposta = passo.apply(comando);
        idempotenciaRepository.save(new SagaIdempotencia(UUID.randomUUID().toString(), comando.sagaId(), comando.tipo(),
                jsonMapper.writeValueAsString(resposta), LocalDateTime.now(clock)));
        return resposta;
    }

    // Recusa comando cuja SAGA já foi compensada
    private MensagemSaga seNaoCompensado(MensagemSaga comando, String tipoCompensacao,
            Function<MensagemSaga, MensagemSaga> passo) {
        if (idempotenciaRepository.findBySagaIdAndTipo(comando.sagaId(), tipoCompensacao).isPresent()) {
            log.warn("[{}] comando ignorado, SAGA ja compensada: sagaId={}", comando.tipo(), comando.sagaId());
            return MensagemSaga.falha(comando.sagaId(), comando.tipo(), agora(), "SAGA já compensada");
        }
        return passo.apply(comando);
    }

    // Verifica se o passo foi concluído com sucesso
    private boolean passoConcluido(String sagaId, String tipo) {
        return idempotenciaRepository.findBySagaIdAndTipo(sagaId, tipo)
                .map(registro -> jsonMapper.readValue(registro.getResposta(), MensagemSaga.class))
                .filter(resposta -> resposta.status() == StatusResposta.SUCESSO)
                .isPresent();
    }

    // R13: insere o gerente; CPF ou e-mail repetido é FALHA
    private MensagemSaga inserir(MensagemSaga comando) {
        Gerente gerente = new Gerente();
        gerente.setCpf(campo(comando, "cpf"));
        gerente.setNome(campo(comando, "nome"));
        gerente.setEmail(campo(comando, "email"));
        gerente.setTelefone(campo(comando, "telefone"));
        try {
            GerenteDTO criado = gerenteService.inserir(gerente);
            log.info("[{}] gerente inserido: cpf={} sagaId={}", comando.tipo(), criado.cpf(), comando.sagaId());
            return MensagemSaga.sucesso(comando.sagaId(), comando.tipo(), agora(), Map.of("cpf", criado.cpf()));
        } catch (CpfJaCadastradoException | EmailJaCadastradoException | IllegalArgumentException e) {
            log.warn("[{}] gerente recusado: {} sagaId={}", comando.tipo(), e.getMessage(), comando.sagaId());
            return MensagemSaga.falha(comando.sagaId(), comando.tipo(), agora(), e.getMessage());
        }
    }

    // Remove o gerente
    private MensagemSaga inserirCompensar(MensagemSaga comando) {
        String cpf = campo(comando, "cpf");
        if (passoConcluido(comando.sagaId(), INSERIR)) {
            gerenteService.remover(cpf);
            log.info("[{}] gerente removido: cpf={} sagaId={}", comando.tipo(), cpf, comando.sagaId());
        } else {
            log.warn("[{}] nada a desfazer, a SAGA nao inseriu o gerente: cpf={} sagaId={}", comando.tipo(), cpf, comando.sagaId());
        }
        return MensagemSaga.sucesso(comando.sagaId(), comando.tipo(), agora(), Map.of());
    }

    // R15: inativa o gerente; o último ativo é FALHA
    private MensagemSaga inativar(MensagemSaga comando) {
        String cpf = campo(comando, "cpf");
        try {
            gerenteService.inativar(cpf);
            log.info("[{}] gerente inativado: cpf={} sagaId={}", comando.tipo(), cpf, comando.sagaId());
            return MensagemSaga.sucesso(comando.sagaId(), comando.tipo(), agora(), Map.of("cpf", cpf));
        } catch (GerenteNaoEncontradoException | UltimoGerenteAtivoException e) {
            log.warn("[{}] remocao recusada: {} sagaId={}", comando.tipo(), e.getMessage(), comando.sagaId());
            return MensagemSaga.falha(comando.sagaId(), comando.tipo(), agora(), e.getMessage());
        }
    }

    // Reativa o gerente
    private MensagemSaga inativarCompensar(MensagemSaga comando) {
        String cpf = campo(comando, "cpf");
        if (passoConcluido(comando.sagaId(), INATIVAR)) {
            gerenteService.reativar(cpf);
            log.info("[{}] gerente reativado: cpf={} sagaId={}", comando.tipo(), cpf, comando.sagaId());
        } else {
            log.warn("[{}] nada a desfazer, a SAGA nao inativou o gerente: cpf={} sagaId={}", comando.tipo(), cpf, comando.sagaId());
        }
        return MensagemSaga.sucesso(comando.sagaId(), comando.tipo(), agora(), Map.of());
    }

    private String campo(MensagemSaga comando, String nome) {
        Object valor = comando.payload() == null ? null : comando.payload().get(nome);
        return valor == null ? null : valor.toString();
    }

    private String agora() {
        return LocalDateTime.now(clock).format(FORMATO_TIMESTAMP);
    }
}
