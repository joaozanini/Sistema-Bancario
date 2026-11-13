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

    // Projeção de uma versão do Event Store. O UPDATE só vale se a conta ainda estiver na versão anterior:
    // devolve 0 quando outra transação já aplicou esta versão, e aí quem chamou não mexe no saldo. O contexto
    // de persistência é limpo depois, para a próxima leitura da conta vir do banco e não da memória
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
