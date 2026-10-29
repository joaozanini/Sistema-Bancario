package com.bantads.ms_auth.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.bantads.ms_auth.dto.MensagemSaga;
import com.bantads.ms_auth.dto.StatusResposta;
import com.bantads.ms_auth.exception.CpfJaCadastradoException;
import com.bantads.ms_auth.exception.LoginJaCadastradoException;
import com.bantads.ms_auth.model.ComandoProcessado;
import com.bantads.ms_auth.repository.ComandoProcessadoRepository;

/**
 * Passos de SAGA do MS Auth. Nenhum log inclui payload: a resposta de
 * auth.criar-usuario carrega a senha em claro.
 */
@Service
public class ComandoSagaService {

    private static final Logger log = LoggerFactory.getLogger(ComandoSagaService.class);

    public static final String CRIAR_USUARIO = "auth.criar-usuario";
    public static final String COMPENSAR_CRIAR_USUARIO = "auth.criar-usuario.compensar";

    // contrato com o orquestrador: este erro faz a solicitacao virar Nao aprovada
    public static final String ERRO_LOGIN_DUPLICADO = "LOGIN_DUPLICADO";

    private static final DateTimeFormatter FORMATO_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final UsuarioService usuarioService;
    private final ComandoProcessadoRepository processados;
    private final Clock clock;

    public ComandoSagaService(UsuarioService usuarioService, ComandoProcessadoRepository processados, Clock clock) {
        this.usuarioService = usuarioService;
        this.processados = processados;
        this.clock = clock;
    }

    public Optional<MensagemSaga> processar(MensagemSaga comando) {
        if (comando.sagaId() == null || comando.tipo() == null) {
            log.warn("Comando sem sagaId ou tipo descartado");
            return Optional.empty();
        }
        Map<String, Object> payload = comando.payload() == null ? Map.of() : comando.payload();

        return switch (comando.tipo()) {
            case CRIAR_USUARIO -> criarUsuario(comando, payload);
            case COMPENSAR_CRIAR_USUARIO -> Optional.of(compensarCriacao(comando, payload));
            default -> Optional.of(MensagemSaga.falha(comando.sagaId(), comando.tipo(), agora(),
                    "Tipo de comando nao reconhecido pelo MS Auth: " + comando.tipo()));
        };
    }

    private Optional<MensagemSaga> criarUsuario(MensagemSaga comando, Map<String, Object> payload) {
        Optional<ComandoProcessado> anterior = processados.findBySagaIdAndTipo(comando.sagaId(), comando.tipo());
        if (anterior.isPresent()) {
            return repetido(comando, anterior.get());
        }

        String cpf = texto(payload, "cpf");
        String email = texto(payload, "email");
        String tipoUsuario = texto(payload, "tipo");
        if (cpf == null || email == null) {
            return Optional.of(registrarFalha(comando, "cpf e email sao obrigatorios"));
        }
        boolean gerente = "GERENTE".equals(tipoUsuario);
        if (tipoUsuario != null && !gerente && !"CLIENTE".equals(tipoUsuario)) {
            return Optional.of(registrarFalha(comando, "Tipo de usuario nao suportado: " + tipoUsuario));
        }
        // R13: a senha vem do formulario e nao volta na resposta
        String senhaInformada = texto(payload, "senha");
        if (gerente && senhaInformada == null) {
            return Optional.of(registrarFalha(comando, "senha e obrigatoria para gerente"));
        }

        Map<String, Object> resposta;
        try {
            if (gerente) {
                usuarioService.criarGerente(comando.sagaId(), cpf, email, senhaInformada);
                resposta = Map.of();
            } else {
                resposta = Map.of("senha", usuarioService.criarCliente(comando.sagaId(), cpf, email));
            }
        } catch (LoginJaCadastradoException e) {
            return Optional.of(registrarFalha(comando, ERRO_LOGIN_DUPLICADO));
        } catch (CpfJaCadastradoException e) {
            return Optional.of(registrarFalha(comando, e.getMessage()));
        }
        registrar(comando, StatusResposta.SUCESSO, null);
        return Optional.of(MensagemSaga.sucesso(comando.sagaId(), comando.tipo(), agora(), resposta));
    }

    /**
     * Sucesso repetido nao e reenviado: a senha nao fica guardada para remontar a
     * resposta. Se a primeira resposta se perdeu, o timeout do passo compensa.
     */
    private Optional<MensagemSaga> repetido(MensagemSaga comando, ComandoProcessado anterior) {
        if (anterior.getStatus() == StatusResposta.SUCESSO) {
            log.info("Comando repetido ignorado: sagaId={} tipo={}", comando.sagaId(), comando.tipo());
            return Optional.empty();
        }
        log.info("Comando repetido, reenviando falha: sagaId={} tipo={}", comando.sagaId(), comando.tipo());
        return Optional.of(MensagemSaga.falha(comando.sagaId(), comando.tipo(), agora(), anterior.getErro()));
    }

    // remover e idempotente, entao a compensacao dispensa o registro de deduplicacao
    private MensagemSaga compensarCriacao(MensagemSaga comando, Map<String, Object> payload) {
        String cpf = texto(payload, "cpf");
        if (cpf == null) {
            return MensagemSaga.falha(comando.sagaId(), comando.tipo(), agora(), "cpf e obrigatorio");
        }
        usuarioService.removerCriadoPelaSaga(comando.sagaId(), cpf);
        return MensagemSaga.sucesso(comando.sagaId(), comando.tipo(), agora(), Map.of());
    }

    private MensagemSaga registrarFalha(MensagemSaga comando, String erro) {
        registrar(comando, StatusResposta.FALHA, erro);
        return MensagemSaga.falha(comando.sagaId(), comando.tipo(), agora(), erro);
    }

    private void registrar(MensagemSaga comando, StatusResposta status, String erro) {
        processados.save(new ComandoProcessado(comando.sagaId(), comando.tipo(), status, erro, clock.instant()));
    }

    private static String texto(Map<String, Object> payload, String chave) {
        Object valor = payload.get(chave);
        return valor == null || valor.toString().isBlank() ? null : valor.toString().trim();
    }

    private String agora() {
        return LocalDateTime.now(clock).format(FORMATO_TIMESTAMP);
    }
}
