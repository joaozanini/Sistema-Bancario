package com.bantads.ms_conta.config;

import com.bantads.ms_conta.service.ResetService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Semeia as contas só com o Event Store vazio */
@Component
public class SeedContas implements CommandLineRunner {

    private final ResetService resetService;

    public SeedContas(ResetService resetService) {
        this.resetService = resetService;
    }

    @Override
    public void run(String... args) {
        resetService.semearSeVazio();
    }
}
