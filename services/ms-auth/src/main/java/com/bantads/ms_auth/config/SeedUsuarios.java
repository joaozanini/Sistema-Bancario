package com.bantads.ms_auth.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.bantads.ms_auth.service.ResetService;

/** Semeia os usuários só com a coleção vazia */
@Component
public class SeedUsuarios implements CommandLineRunner {

    private final ResetService resetService;

    public SeedUsuarios(ResetService resetService) {
        this.resetService = resetService;
    }

    @Override
    public void run(String... args) {
        resetService.semearSeVazio();
    }
}
