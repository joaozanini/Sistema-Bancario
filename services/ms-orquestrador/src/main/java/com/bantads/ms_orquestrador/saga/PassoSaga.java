package com.bantads.ms_orquestrador.saga;

import java.util.Map;
import java.util.function.BiFunction;
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
        Predicate<Map<String, Object>> condicao) {

    private static final Predicate<Map<String, Object>> SEMPRE = dados -> true;

    public static PassoSaga transacional(String fila, String tipoComando,
            Function<Map<String, Object>, Map<String, Object>> payloadComando,
            String tipoCompensacao,
            BiFunction<Map<String, Object>, String, Map<String, Object>> payloadCompensacao) {
        return new PassoSaga(fila, tipoComando, payloadComando, tipoCompensacao, payloadCompensacao, false, SEMPRE);
    }

    public static PassoSaga consulta(String fila, String tipoComando,
            Function<Map<String, Object>, Map<String, Object>> payloadComando) {
        return new PassoSaga(fila, tipoComando, payloadComando, null, null, false, SEMPRE);
    }

    public static PassoSaga fireAndForget(String fila, String tipoComando,
            Function<Map<String, Object>, Map<String, Object>> payloadComando) {
        return new PassoSaga(fila, tipoComando, payloadComando, null, null, true, SEMPRE);
    }

    public PassoSaga somenteSe(Predicate<Map<String, Object>> novaCondicao) {
        return new PassoSaga(fila, tipoComando, payloadComando, tipoCompensacao, payloadCompensacao, fireAndForget,
                novaCondicao);
    }

    public boolean executa(Map<String, Object> dados) {
        return condicao.test(dados);
    }

    public boolean temCompensacao() {
        return tipoCompensacao != null;
    }
}
