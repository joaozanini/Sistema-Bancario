package com.bantads.ms_auth.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.bantads.ms_auth.exception.CredenciaisInvalidasException;
import com.bantads.ms_auth.model.Usuario;
import com.bantads.ms_auth.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    private final String hashDescartavel;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.hashDescartavel = passwordEncoder.encode("usuario-inexistente");
    }

    public Usuario autenticar(String login, String senha) {
        Optional<Usuario> encontrado = repository.findByLogin(login);
        String hashArmazenado = encontrado.map(Usuario::getSenhaHash).orElse(hashDescartavel);

        if (!passwordEncoder.matches(senha, hashArmazenado) || encontrado.isEmpty()) {
            throw new CredenciaisInvalidasException();
        }
        return encontrado.get();
    }

}
