package com.bantads.ms_conta.repository;

import com.bantads.ms_conta.model.ContaQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContaQueryRepository extends JpaRepository<ContaQuery, String> {

    Optional<ContaQuery> findByCpfCliente(String cpfCliente);
}
