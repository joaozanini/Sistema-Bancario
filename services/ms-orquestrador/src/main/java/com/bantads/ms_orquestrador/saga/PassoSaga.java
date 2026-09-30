package com.bantads.ms_orquestrador.saga;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Um passo da SAGA. Os montadores recebem os dados acumulados e devolvem o
 * payload da mensagem, o de compensação recebe tambem o erro que causou a falha.
 */
public record PassoSaga(
        String fila,
        String tipoComando,
        Function<Map<String, Object>, Map<String, Object>> payloadComando,
        String tipoCompensacao,
        BiFunction<Map<String, Object>, String, Map<String, Object>> payloadCompensacao,
        boolean fireAndForget) {

    public static PassoSaga transacional(String fila, String tipoComando,
            Function<Map<String, Object>, Map<String, Object>> payloadComando,
            String tipoCompensacao,
            BiFunction<Map<String, Object>, String, Map<String, Object>> payloadCompensacao) {
        return new PassoSaga(fila, tipoComando, payloadComando, tipoCompensacao, payloadCompensacao, false);
    }

    public static PassoSaga consulta(String fila, String tipoComando,
            Function<Map<String, Object>, Map<String, Object>> payloadComando) {
        return new PassoSaga(fila, tipoComando, payloadComando, null, null, false);
    }

    public static PassoSaga fireAndForget(String fila, String tipoComando,
            Function<Map<String, Object>, Map<String, Object>> payloadComando) {
        return new PassoSaga(fila, tipoComando, payloadComando, null, null, true);
    }

    public boolean temCompensacao() {
        return tipoCompensacao != null;
    }
}
