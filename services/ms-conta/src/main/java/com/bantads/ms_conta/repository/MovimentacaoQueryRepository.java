package com.bantads.ms_conta.repository;

import com.bantads.ms_conta.model.MovimentacaoQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimentacaoQueryRepository extends JpaRepository<MovimentacaoQuery, String> {

    List<MovimentacaoQuery> findByNumeroContaOrderByDataHoraAscVersaoAsc(String numeroConta);

    List<MovimentacaoQuery> findByNumeroContaAndDataHoraBetweenOrderByDataHoraAscVersaoAsc(
            String numeroConta,
            LocalDateTime inicio,
            LocalDateTime fim
    );

    List<MovimentacaoQuery> findByNumeroContaAndDataHoraLessThanOrderByDataHoraAscVersaoAsc(
            String numeroConta,
            LocalDateTime inicio
    );

    void deleteByNumeroConta(String numeroConta);
}
