package com.bantads.ms_conta.repository;

import com.bantads.ms_conta.model.EventoConta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventoContaRepository extends JpaRepository<EventoConta, String> {

    List<EventoConta> findByObjetoIdOrderByVersaoAsc(String objetoId);

    Optional<EventoConta> findTopByObjetoIdOrderByVersaoDesc(String objetoId);

    boolean existsByObjetoId(String objetoId);

    @Query("SELECT COALESCE(MAX(e.versao), 0) FROM EventoConta e WHERE e.objetoId = :objetoId")
    Integer findMaxVersaoByObjetoId(@Param("objetoId") String objetoId);
}