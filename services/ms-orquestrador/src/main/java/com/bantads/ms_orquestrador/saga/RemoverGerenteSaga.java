package com.bantads.ms_orquestrador.saga;

import static com.bantads.ms_orquestrador.saga.DefinicaoSaga.campos;

import com.bantads.ms_orquestrador.config.RabbitMQConfig;
import com.bantads.ms_orquestrador.model.Job;
import com.bantads.ms_orquestrador.repository.SessaoRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * R15. O gerente e inativado antes de listar os ativos, para que o proprio
 * removido nunca apareca como destino das contas. O encerramento da sessao no
 * Redis e um passo do proprio Orquestrador, sem mensagem e sem compensacao: se
 * a SAGA falhar depois dele, o gerente reativado faz novo login. Sem contas a
 * transferir, os passos de consulta ao MS Cliente e de e-mail sao pulados.
 */
@Component
public class RemoverGerenteSaga implements DefinicaoSaga {

    public static final String TIPO = "remover-gerente";

    public static final String MENSAGEM_SUCESSO = "Gerente removido com sucesso.";

    private final List<PassoSaga> passos;

    public RemoverGerenteSaga(SessaoRepository sessoes) {
        this.passos = List.of(
                PassoSaga.transacional(RabbitMQConfig.FILA_GERENTE_CMD,
                        "gerente.inativar", dados -> campos(dados, "cpf"),
                        "gerente.inativar.compensar", (dados, erro) -> campos(dados, "cpf")),
                PassoSaga.transacional(RabbitMQConfig.FILA_AUTH_CMD,
                        "auth.desativar-usuario", dados -> campos(dados, "cpf"),
                        "auth.desativar-usuario.compensar", (dados, erro) -> campos(dados, "cpf")),
                PassoSaga.local("orquestrador.encerrar-sessao",
                        dados -> sessoes.encerrarPorCpf((String) dados.get("cpf"))),
                PassoSaga.consulta(RabbitMQConfig.FILA_GERENTE_CMD,
                        "gerente.listar-ativos", dados -> Map.of()),
                PassoSaga.transacional(RabbitMQConfig.FILA_CONTA_CMD,
                        "conta.transferir-contas", RemoverGerenteSaga::comandoTransferir,
                        "conta.transferir-contas.compensar", RemoverGerenteSaga::compensacaoTransferir),
                PassoSaga.consulta(RabbitMQConfig.FILA_CLIENTE_CMD,
                        "cliente.obter-dados", dados -> Map.of("cpfs", dados.get("cpfsClientes")))
                        .somenteSe(RemoverGerenteSaga::temClientesTransferidos),
                PassoSaga.fireAndForget(RabbitMQConfig.FILA_EMAIL_CMD,
                        "email.remocao-gerente", dados -> campos(dados, "clientes"))
                        .somenteSe(RemoverGerenteSaga::temClientesTransferidos));
    }

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public List<PassoSaga> passos() {
        return passos;
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
    public Job jobConcluido(String sagaId, Map<String, Object> dados) {
        return Job.concluidoInline(sagaId, dominioJob(), Map.of("mensagem", MENSAGEM_SUCESSO));
    }

    private static boolean temClientesTransferidos(Map<String, Object> dados) {
        return dados.get("cpfsClientes") instanceof List<?> cpfs && !cpfs.isEmpty();
    }

    private static Map<String, Object> comandoTransferir(Map<String, Object> dados) {
        // defesa em profundidade: o removido ja esta inativo, mas nunca e destino
        List<?> destinos = dados.get("gerentes") instanceof List<?> ativos
                ? ativos.stream().filter(cpf -> !cpf.equals(dados.get("cpf"))).toList()
                : List.of();
        return Map.of("gerenteRemovidoCpf", dados.get("cpf"), "gerentes", destinos);
    }

    // cpfsClientes so existe se o MS Conta chegou a responder; num timeout ele
    // reassocia as contas que moveu nesta SAGA, identificadas pelo sagaId
    private static Map<String, Object> compensacaoTransferir(Map<String, Object> dados, String erro) {
        Map<String, Object> payload = campos(dados, "cpfsClientes");
        payload.put("gerenteOriginalCpf", dados.get("cpf"));
        return payload;
    }
}
