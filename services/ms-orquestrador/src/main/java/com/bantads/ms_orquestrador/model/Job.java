package com.bantads.ms_orquestrador.model;

public record Job(
        String jobId,
        StatusJob status,
        String resultType,
        String dominio,
        String resourceId,
        String erro) {

    public static Job concluidoComRecurso(String jobId, String dominio, String resourceId) {
        return new Job(jobId, StatusJob.CONCLUIDO, "resource", dominio, resourceId, null);
    }

    public static Job falha(String jobId, String dominio, String erro) {
        return new Job(jobId, StatusJob.FALHA, null, dominio, null, erro);
    }
}
