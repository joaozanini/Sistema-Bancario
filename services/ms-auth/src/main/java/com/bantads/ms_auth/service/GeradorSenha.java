package com.bantads.ms_auth.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class GeradorSenha {

    // Caracteres da senha, sem os ambíguos
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int TAMANHO = 8;

    private final SecureRandom random = new SecureRandom();

    public String gerar() {
        StringBuilder senha = new StringBuilder(TAMANHO);
        for (int i = 0; i < TAMANHO; i++) {
            senha.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return senha.toString();
    }
}
