package com.bantads.ms_orquestrador.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Segredos da SAGA guardados so em memoria: os do payload inicial (a senha
 * informada na R13) e os que chegam nas respostas (a senha gerada pelo MS Auth
 * na R9). Nunca sao logados nem serializados. Se o Orquestrador reiniciar no
 * meio da SAGA eles se perdem: o passo que precisava deles falha e a SAGA
 * compensa.
 */
@Component
public class SegredosSaga {

    private final Map<String, Map<String, Object>> porSaga = new ConcurrentHashMap<>();

    public void guardar(String sagaId, Map<String, Object> segredos) {
        if (!segredos.isEmpty()) {
            porSaga.put(sagaId, new HashMap<>(segredos));
        }
    }

    public void acrescentar(String sagaId, Map<String, Object> novos) {
        if (!novos.isEmpty()) {
            porSaga.merge(sagaId, new HashMap<>(novos), (atuais, adicionados) -> {
                Map<String, Object> unidos = new HashMap<>(atuais);
                unidos.putAll(adicionados);
                return unidos;
            });
        }
    }

    public Map<String, Object> buscar(String sagaId) {
        return porSaga.getOrDefault(sagaId, Map.of());
    }

    public void descartar(String sagaId) {
        porSaga.remove(sagaId);
    }
}
