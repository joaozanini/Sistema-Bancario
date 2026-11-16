package com.bantads.ms_orquestrador.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Segredos da SAGA guardados so em memoria */
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
