package com.bantads.ms_auth.repository;

import com.bantads.ms_auth.model.ComandoProcessado;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ComandoProcessadoRepository extends MongoRepository<ComandoProcessado, String> {

    Optional<ComandoProcessado> findBySagaIdAndTipo(String sagaId, String tipo);
}
