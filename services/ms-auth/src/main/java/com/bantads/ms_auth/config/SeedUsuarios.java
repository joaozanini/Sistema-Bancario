package com.bantads.ms_auth.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.bantads.ms_auth.model.TipoUsuario;
import com.bantads.ms_auth.model.Usuario;
import com.bantads.ms_auth.repository.UsuarioRepository;

/**
 * Usuarios pre-cadastrados do enunciado (secao 4). So insere com a colecao vazia,
 * para nao duplicar a cada restart.
 */
@Component
public class SeedUsuarios implements CommandLineRunner {

    private static final String SENHA_SEED = "tads";

    private record Seed(String cpf, TipoUsuario tipo, String login) {
    }

    private static final List<Seed> SEEDS = List.of(
            new Seed("12912861012", TipoUsuario.CLIENTE, "cli1@bantads.com.br"),
            new Seed("09506382000", TipoUsuario.CLIENTE, "cli2@bantads.com.br"),
            new Seed("85733854057", TipoUsuario.CLIENTE, "cli3@bantads.com.br"),
            new Seed("58872160006", TipoUsuario.CLIENTE, "cli4@bantads.com.br"),
            new Seed("76179646090", TipoUsuario.CLIENTE, "cli5@bantads.com.br"),
            new Seed("98574307084", TipoUsuario.GERENTE, "ger1@bantads.com.br"),
            new Seed("64065268052", TipoUsuario.GERENTE, "ger2@bantads.com.br"),
            new Seed("23862179060", TipoUsuario.GERENTE, "ger3@bantads.com.br"),
            new Seed("40501740066", TipoUsuario.GERENTE, "ger4@bantads.com.br"));

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public SeedUsuarios(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        // cada usuario recebe o proprio hash (salt aleatorio), mesmo com a mesma senha
        List<Usuario> usuarios = SEEDS.stream()
                .map(s -> new Usuario(s.cpf(), s.tipo(), s.login(), passwordEncoder.encode(SENHA_SEED)))
                .toList();
        repository.saveAll(usuarios);
    }
}
