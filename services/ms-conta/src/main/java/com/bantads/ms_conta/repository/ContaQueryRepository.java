package com.bantads.ms_conta.repository;

import com.bantads.ms_conta.model.ContaQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface ContaQueryRepository extends JpaRepository<ContaQuery, String> {

    Optional<ContaQuery> findByCpfCliente(String cpfCliente);

    // Aplica a versão só se a conta estiver na anterior; devolve 0 se já aplicada
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ContaQuery c set c.saldo = c.saldo + :variacao, c.ultimaVersao = :versao "
            + "where c.numeroConta = :numeroConta and c.ultimaVersao = :versaoAnterior")
    int aplicarVersao(@Param("numeroConta") String numeroConta,
                      @Param("versaoAnterior") int versaoAnterior,
                      @Param("versao") int versao,
                      @Param("variacao") BigDecimal variacao);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ContaQuery c set c.gerenteCpf = :gerenteCpf, c.ultimaVersao = :versao "
            + "where c.numeroConta = :numeroConta and c.ultimaVersao = :versaoAnterior")
    int aplicarTrocaDeGerente(@Param("numeroConta") String numeroConta,
                              @Param("versaoAnterior") int versaoAnterior,
                              @Param("versao") int versao,
                              @Param("gerenteCpf") String gerenteCpf);
}
