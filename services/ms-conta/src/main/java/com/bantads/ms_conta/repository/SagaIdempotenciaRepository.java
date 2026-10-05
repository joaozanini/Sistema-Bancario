package com.bantads.ms_conta.repository;

import com.bantads.ms_conta.model.SagaIdempotencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SagaIdempotenciaRepository extends JpaRepository<SagaIdempotencia, String> {

    Optional<SagaIdempotencia> findBySagaIdAndTipo(String sagaId, String tipo);

    boolean existsBySagaIdAndTipo(String sagaId, String tipo);
}
