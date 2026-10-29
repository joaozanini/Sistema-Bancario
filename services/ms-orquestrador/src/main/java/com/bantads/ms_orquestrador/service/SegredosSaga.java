package com.bantads.ms_orquestrador.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Segredos do payload inicial (a senha informada na R13) guardados so em
 * memoria. Se o Orquestrador reiniciar no meio da SAGA eles se perdem: o passo
 * que precisava deles falha e a SAGA compensa.
 */
@Component
public class SegredosSaga {

    private final Map<String, Map<String, Object>> porSaga = new ConcurrentHashMap<>();

    public void guardar(String sagaId, Map<String, Object> segredos) {
        if (!segredos.isEmpty()) {
            porSaga.put(sagaId, new HashMap<>(segredos));
        }
    }

    public Map<String, Object> buscar(String sagaId) {
        return porSaga.getOrDefault(sagaId, Map.of());
    }

    public void descartar(String sagaId) {
        porSaga.remove(sagaId);
    }
}
