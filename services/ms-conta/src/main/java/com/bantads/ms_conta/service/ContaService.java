package com.bantads.ms_conta.service;

import com.bantads.ms_conta.repository.EventoContaRepository;
import org.springframework.stereotype.Service;

@Service
public class ContaService {

    private final EventoContaRepository repository;

    public ContaService(EventoContaRepository repository) {
        this.repository = repository;
    }

}