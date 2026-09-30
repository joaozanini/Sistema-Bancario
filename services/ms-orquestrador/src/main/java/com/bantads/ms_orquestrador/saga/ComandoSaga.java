package com.bantads.ms_orquestrador.saga;

import java.util.Map;

public record ComandoSaga(String fila, String tipo, Map<String, Object> payload) {
}
