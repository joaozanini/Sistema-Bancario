package com.bantads.ms_conta.repository;

import com.bantads.ms_conta.model.EventoConta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventoContaRepository extends JpaRepository<EventoConta, String> {
}