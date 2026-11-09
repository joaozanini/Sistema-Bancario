package com.bantads.ms_orquestrador.saga;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Um passo da SAGA. Os montadores recebem os dados acumulados e devolvem o
 * payload da mensagem, o de compensação recebe tambem o erro que causou a falha.
 * Passo cuja condicao nao vale para os dados acumulados e pulado, e tambem nao
 * e compensado. As chaves exigidas precisam estar nos dados no momento do
 * envio; se faltar alguma, o passo falha e a SAGA compensa.
 */
public record PassoSaga(
        String fila,
        String tipoComando,
        Function<Map<String, Object>, Map<String, Object>> payloadComando,
        String tipoCompensacao,
        BiFunction<Map<String, Object>, String, Map<String, Object>> payloadCompensacao,
        boolean fireAndForget,
        Predicate<Map<String, Object>> condicao,
        Consumer<Map<String, Object>> acaoLocal,
        Set<String> chavesExigidas) {

    private static final Predicate<Map<String, Object>> SEMPRE = dados -> true;

    public static PassoSaga transacional(String fila, String tipoComando,
            Function<Map<String, Object>, Map<String, Object>> payloadComando,
            String tipoCompensacao,
            BiFunction<Map<String, Object>, String, Map<String, Object>> payloadCompensacao) {
        return new PassoSaga(fila, tipoComando, payloadComando, tipoCompensacao, payloadCompensacao, false, SEMPRE,
                null, Set.of());
    }

    public static PassoSaga consulta(String fila, String tipoComando,
            Function<Map<String, Object>, Map<String, Object>> payloadComando) {
        return new PassoSaga(fila, tipoComando, payloadComando, null, null, false, SEMPRE, null, Set.of());
    }

    public static PassoSaga fireAndForget(String fila, String tipoComando,
            Function<Map<String, Object>, Map<String, Object>> payloadComando) {
        return new PassoSaga(fila, tipoComando, payloadComando, null, null, true, SEMPRE, null, Set.of());
    }

    /**
     * Passo executado no proprio Orquestrador, sem mensagem: roda quando a SAGA
     * chega nele, sem aguardar resposta e sem compensacao.
     */
    public static PassoSaga local(String nome, Consumer<Map<String, Object>> acao) {
        return new PassoSaga(null, nome, dados -> Map.of(), null, null, true, SEMPRE, acao, Set.of());
    }

    public PassoSaga somenteSe(Predicate<Map<String, Object>> novaCondicao) {
        return new PassoSaga(fila, tipoComando, payloadComando, tipoCompensacao, payloadCompensacao, fireAndForget,
                novaCondicao, acaoLocal, chavesExigidas);
    }

    public PassoSaga exige(String... chaves) {
        return new PassoSaga(fila, tipoComando, payloadComando, tipoCompensacao, payloadCompensacao, fireAndForget,
                condicao, acaoLocal, Set.of(chaves));
    }

    public List<String> chavesFaltando(Map<String, Object> dados) {
        return chavesExigidas.stream().filter(chave -> dados.get(chave) == null).sorted().toList();
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
