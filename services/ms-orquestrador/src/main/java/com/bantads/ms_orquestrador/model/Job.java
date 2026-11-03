package com.bantads.ms_orquestrador.model;

import com.fasterxml.jackson.annotation.JsonInclude;

public record Job(
        String jobId,
        StatusJob status,
        String resultType,
        String dominio,
        String resourceId,
        String erro,
        // so aparece no resultType "inline"; nos demais o formato da 5.8 nao tem a chave
        @JsonInclude(JsonInclude.Include.NON_NULL) Object resultado) {

    public static Job concluidoComRecurso(String jobId, String dominio, String resourceId) {
        return new Job(jobId, StatusJob.CONCLUIDO, "resource", dominio, resourceId, null, null);
    }

    public static Job concluidoInline(String jobId, String dominio, Object resultado) {
        return new Job(jobId, StatusJob.CONCLUIDO, "inline", dominio, null, null, resultado);
    }

    public static Job falha(String jobId, String dominio, String erro) {
        return new Job(jobId, StatusJob.FALHA, null, dominio, null, erro, null);
    }
}
