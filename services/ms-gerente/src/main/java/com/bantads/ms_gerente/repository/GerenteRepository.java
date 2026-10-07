package com.bantads.ms_gerente.repository;

import com.bantads.ms_gerente.model.Gerente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GerenteRepository extends JpaRepository<Gerente, String> {

    boolean existsByEmailAndCpfNot(String email, String cpf);
}
