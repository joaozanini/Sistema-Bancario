package com.bantads.ms_orquestrador.saga;

import com.bantads.ms_orquestrador.model.Job;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface DefinicaoSaga {

    String tipo();

    List<PassoSaga> passos();

    String dominioJob();

    String resourceId(Map<String, Object> dados);

    /**
     * Chaves de resposta que seguem apenas para o comando seguinte e nunca
     * entram no estado salvo no Redis.
     */
    /**
     * Job gravado quando a SAGA conclui. Por padrao aponta para o recurso criado
     * (resultType "resource"); a SAGA que devolve so uma mensagem usa "inline".
     */
    default Job jobConcluido(String sagaId, Map<String, Object> dados) {
        return Job.concluidoComRecurso(sagaId, dominioJob(), resourceId(dados));
    }

    default Set<String> chavesTransitorias() {
        return Set.of();
    }

    /**
     * Chaves do payload inicial que ficam so na memoria do Orquestrador ate a
     * SAGA terminar e nunca entram no estado salvo no Redis.
     */
    default Set<String> chavesSecretasIniciais() {
        return Set.of();
    }

    default Optional<ComandoSaga> emailFalha(Map<String, Object> dados) {
        return Optional.empty();
    }

    default String mensagemErroJob(String erro) {
        return erro;
    }

    static Map<String, Object> campos(Map<String, Object> dados, String... chaves) {
        Map<String, Object> selecionados = new LinkedHashMap<>();
        for (String chave : chaves) {
            if (dados.get(chave) != null) {
                selecionados.put(chave, dados.get(chave));
            }
        }
        return selecionados;
    }
}
