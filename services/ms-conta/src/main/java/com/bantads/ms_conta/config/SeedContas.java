package com.bantads.ms_conta.config;

import com.bantads.ms_conta.service.ResetService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * So semeia com o event store vazio, para nao apagar as operacoes a cada restart.
 */
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
