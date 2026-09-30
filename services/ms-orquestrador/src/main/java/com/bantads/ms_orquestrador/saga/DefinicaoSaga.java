package com.bantads.ms_orquestrador.saga;

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
    default Set<String> chavesTransitorias() {
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
