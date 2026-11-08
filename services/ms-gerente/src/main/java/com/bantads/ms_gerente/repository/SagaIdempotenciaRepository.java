package com.bantads.ms_gerente.repository;

import com.bantads.ms_gerente.model.SagaIdempotencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SagaIdempotenciaRepository extends JpaRepository<SagaIdempotencia, String> {

    Optional<SagaIdempotencia> findBySagaIdAndTipo(String sagaId, String tipo);
}
