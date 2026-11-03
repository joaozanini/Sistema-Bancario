package com.bantads.ms_gerente.repository;

import com.bantads.ms_gerente.model.Gerente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GerenteRepository extends JpaRepository<Gerente, String> {

    boolean existsByCpf(String cpf);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailAndCpfNot(String email, String cpf);

    boolean existsByEmailIgnoreCaseAndCpfNot(String email, String cpf);

    Optional<Gerente> findByEmail(String email);

    Optional<Gerente> findByEmailIgnoreCase(String email);
}
