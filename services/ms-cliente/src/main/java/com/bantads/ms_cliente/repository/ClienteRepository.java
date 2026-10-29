package com.bantads.ms_cliente.repository;

import com.bantads.ms_cliente.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, String> {
    boolean existsByCpf(String cpf);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
}
