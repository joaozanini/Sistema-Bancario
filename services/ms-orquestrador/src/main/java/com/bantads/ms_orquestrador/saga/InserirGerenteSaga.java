package com.bantads.ms_orquestrador.saga;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.bantads.ms_orquestrador.config.RabbitMQConfig;
import static com.bantads.ms_orquestrador.saga.DefinicaoSaga.campos;

/**
 * R13. A senha informada no formulario chega no payload inicial e fica so em
 * memoria ate o comando do MS Auth. Sem conta a transferir (primeiro gerente ou
 * todos com no maximo uma conta), os passos de atribuicao, consulta ao MS
 * Cliente e e-mail sao pulados e o gerente e criado sem contas.
 */
@Component
public class InserirGerenteSaga implements DefinicaoSaga {

    public static final String TIPO = "inserir-gerente";
    public static final String ERRO_LOGIN_DUPLICADO = "LOGIN_DUPLICADO";

    static final String CHAVE_SENHA = "senha";

    private static final List<PassoSaga> PASSOS = List.of(
            PassoSaga.transacional(RabbitMQConfig.FILA_GERENTE_CMD,
                    "gerente.inserir", dados -> campos(dados, "cpf", "nome", "email", "telefone"),
                    "gerente.inserir.compensar", (dados, erro) -> campos(dados, "cpf")),
            PassoSaga.transacional(RabbitMQConfig.FILA_AUTH_CMD,
                    "auth.criar-usuario", InserirGerenteSaga::comandoCriarUsuario,
                    "auth.criar-usuario.compensar", (dados, erro) -> campos(dados, "cpf")),
            PassoSaga.consulta(RabbitMQConfig.FILA_CONTA_CMD,
                    "conta.escolher-conta-transferencia", dados -> campos(dados, "cpf")),
            PassoSaga.transacional(RabbitMQConfig.FILA_CONTA_CMD,
                    "conta.atribuir-gerente", InserirGerenteSaga::comandoAtribuirConta,
                    "conta.atribuir-gerente.compensar",
                    (dados, erro) -> campos(dados, "numeroConta", "gerenteAntigoCpf"))
                    .somenteSe(InserirGerenteSaga::temContaATransferir),
            PassoSaga.consulta(RabbitMQConfig.FILA_CLIENTE_CMD,
                    "cliente.obter-dados", InserirGerenteSaga::comandoObterCliente)
                    .somenteSe(InserirGerenteSaga::temContaATransferir),
            PassoSaga.fireAndForget(RabbitMQConfig.FILA_EMAIL_CMD,
                    "email.troca-gerente", InserirGerenteSaga::comandoEmail)
                    .somenteSe(InserirGerenteSaga::temContaATransferir));

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
        return "gerentes";
    }

    @Override
    public String resourceId(Map<String, Object> dados) {
        return (String) dados.get("cpf");
    }

    @Override
    public Set<String> chavesSecretasIniciais() {
        return Set.of(CHAVE_SENHA);
    }

    @Override
    public String mensagemErroJob(String erro) {
        return ERRO_LOGIN_DUPLICADO.equals(erro) ? "E-mail já cadastrado" : erro;
    }

    private static boolean temContaATransferir(Map<String, Object> dados) {
        return Boolean.TRUE.equals(dados.get("temConta"));
    }

    private static Map<String, Object> comandoCriarUsuario(Map<String, Object> dados) {
        Map<String, Object> payload = campos(dados, "cpf", "email", CHAVE_SENHA);
        payload.put("tipo", "GERENTE");
        return payload;
    }

    private static Map<String, Object> comandoAtribuirConta(Map<String, Object> dados) {
        Map<String, Object> payload = campos(dados, "numeroConta", "gerenteAntigoCpf");
        payload.put("novoGerenteCpf", dados.get("cpf"));
        return payload;
    }

    // lista para o mesmo comando servir a R15, que transfere varias contas
    private static Map<String, Object> comandoObterCliente(Map<String, Object> dados) {
        return Map.of("cpfs", List.of(dados.get("cpfCliente")));
    }

    private static Map<String, Object> comandoEmail(Map<String, Object> dados) {
        Map<String, Object> payload = campos(dados, "clientes");
        payload.put("nomeGerente", dados.get("nome"));
        return payload;
    }
}
