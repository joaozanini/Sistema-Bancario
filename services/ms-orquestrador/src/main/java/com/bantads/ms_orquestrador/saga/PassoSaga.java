package com.bantads.ms_orquestrador.saga;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Um passo da SAGA. Os montadores recebem os dados acumulados e devolvem o
 * payload da mensagem, o de compensação recebe tambem o erro que causou a falha.
 * Passo cuja condicao nao vale para os dados acumulados e pulado, e tambem nao
 * e compensado.
 */
public record PassoSaga(
        String fila,
        String tipoComando,
        Function<Map<String, Object>, Map<String, Object>> payloadComando,
        String tipoCompensacao,
        BiFunction<Map<String, Object>, String, Map<String, Object>> payloadCompensacao,
        boolean fireAndForget,
        Predicate<Map<String, Object>> condicao,
        Consumer<Map<String, Object>> acaoLocal) {

    private static final Predicate<Map<String, Object>> SEMPRE = dados -> true;

    public static PassoSaga transacional(String fila, String tipoComando,
            Function<Map<String, Object>, Map<String, Object>> payloadComando,
            String tipoCompensacao,
            BiFunction<Map<String, Object>, String, Map<String, Object>> payloadCompensacao) {
        return new PassoSaga(fila, tipoComando, payloadComando, tipoCompensacao, payloadCompensacao, false, SEMPRE, null);
    }

    public static PassoSaga consulta(String fila, String tipoComando,
            Function<Map<String, Object>, Map<String, Object>> payloadComando) {
        return new PassoSaga(fila, tipoComando, payloadComando, null, null, false, SEMPRE, null);
    }

    public static PassoSaga fireAndForget(String fila, String tipoComando,
            Function<Map<String, Object>, Map<String, Object>> payloadComando) {
        return new PassoSaga(fila, tipoComando, payloadComando, null, null, true, SEMPRE, null);
    }

    /**
     * Passo executado no proprio Orquestrador, sem mensagem: roda quando a SAGA
     * chega nele, sem aguardar resposta e sem compensacao.
     */
    public static PassoSaga local(String nome, Consumer<Map<String, Object>> acao) {
        return new PassoSaga(null, nome, dados -> Map.of(), null, null, true, SEMPRE, acao);
    }

    public PassoSaga somenteSe(Predicate<Map<String, Object>> novaCondicao) {
        return new PassoSaga(fila, tipoComando, payloadComando, tipoCompensacao, payloadCompensacao, fireAndForget,
                novaCondicao, acaoLocal);
    }

    public boolean executa(Map<String, Object> dados) {
        return condicao.test(dados);
    }

    public boolean ehLocal() {
        return acaoLocal != null;
    }

    public boolean temCompensacao() {
        return tipoCompensacao != null;
    }
}
