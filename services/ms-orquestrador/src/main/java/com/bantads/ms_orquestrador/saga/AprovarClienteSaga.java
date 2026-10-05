package com.bantads.ms_orquestrador.saga;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.bantads.ms_orquestrador.config.RabbitMQConfig;
import static com.bantads.ms_orquestrador.saga.DefinicaoSaga.campos;

/**
 * R9. O MS Auth vem logo antes do e-mail para que a senha em claro da resposta
 * dele siga direto para o comando de e-mail sem passar pelo estado no Redis.
 */
@Component
public class AprovarClienteSaga implements DefinicaoSaga {

    public static final String TIPO = "aprovar-cliente";
    public static final String ERRO_LOGIN_DUPLICADO = "LOGIN_DUPLICADO";

    static final String CHAVE_SENHA = "senha";

    private static final List<PassoSaga> PASSOS = List.of(
            PassoSaga.transacional(RabbitMQConfig.FILA_CLIENTE_CMD,
                    "cliente.aprovar-solicitacao", dados -> campos(dados, "cpf"),
                    "cliente.aprovar-solicitacao.compensar", AprovarClienteSaga::compensacaoSolicitacao),
            PassoSaga.consulta(RabbitMQConfig.FILA_GERENTE_CMD,
                    "gerente.listar-ativos", dados -> Map.of()),
            PassoSaga.consulta(RabbitMQConfig.FILA_CONTA_CMD,
                    "conta.escolher-gerente", dados -> campos(dados, "gerentes")),
            PassoSaga.transacional(RabbitMQConfig.FILA_CLIENTE_CMD,
                    "cliente.criar-cliente", dados -> campos(dados, "cpf"),
                    "cliente.criar-cliente.compensar", (dados, erro) -> campos(dados, "cpf")),
            PassoSaga.transacional(RabbitMQConfig.FILA_CONTA_CMD,
                    "conta.criar-conta", dados -> campos(dados, "cpf", "cpfGerente"),
                    "conta.criar-conta.compensar", (dados, erro) -> campos(dados, "cpf")),
            PassoSaga.transacional(RabbitMQConfig.FILA_AUTH_CMD,
                    "auth.criar-usuario", AprovarClienteSaga::comandoCriarUsuario,
                    "auth.criar-usuario.compensar", (dados, erro) -> campos(dados, "cpf")),
            PassoSaga.fireAndForget(RabbitMQConfig.FILA_EMAIL_CMD,
                    "email.enviar-senha", dados -> campos(dados, "email", "nome", CHAVE_SENHA)));

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public List<PassoSaga> passos() {
        return PASSOS;
    }

    @Override
    public String dominioJob() {
        return "clientes";
    }

    @Override
    public String resourceId(Map<String, Object> dados) {
        return (String) dados.get("cpf");
    }

    @Override
    public Set<String> chavesTransitorias() {
        return Set.of(CHAVE_SENHA);
    }

    @Override
    public Optional<ComandoSaga> emailFalha(Map<String, Object> dados) {
        // nome e e-mail so existem depois do passo 1; sem eles nao ha para quem avisar
        if (dados.get("email") == null) {
            return Optional.empty();
        }
        return Optional.of(new ComandoSaga(RabbitMQConfig.FILA_EMAIL_CMD,
                "email.solicitacao-nao-efetuada", campos(dados, "email", "nome")));
    }

    @Override
    public String mensagemErroJob(String erro) {
        return ERRO_LOGIN_DUPLICADO.equals(erro) ? "E-mail já cadastrado" : erro;
    }

    private static Map<String, Object> comandoCriarUsuario(Map<String, Object> dados) {
        Map<String, Object> payload = campos(dados, "cpf", "email");
        payload.put("tipo", "CLIENTE");
        return payload;
    }

    // Login duplicado nunca vai passar numa nova tentativa: a solicitacao sai de
    // Pendente para nao ficar travada.
    private static Map<String, Object> compensacaoSolicitacao(Map<String, Object> dados, String erro) {
        Map<String, Object> payload = campos(dados, "cpf");
        if (ERRO_LOGIN_DUPLICADO.equals(erro)) {
            payload.put("statusSolicitacao", "NAO_APROVADA");
            payload.put("motivo", "E-mail já cadastrado");
        } else {
            payload.put("statusSolicitacao", "PENDENTE");
        }
        return payload;
    }
}
