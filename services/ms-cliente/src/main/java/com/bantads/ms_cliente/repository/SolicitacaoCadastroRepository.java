package com.bantads.ms_cliente.repository;

import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SolicitacaoCadastroRepository extends JpaRepository<SolicitacaoCadastro, UUID> {
    boolean existsByCpf(String cpf);
    boolean existsByEmail(String email);
}