package com.bantads.ms_auth.service;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.bantads.ms_auth.exception.CpfJaCadastradoException;
import com.bantads.ms_auth.exception.CredenciaisInvalidasException;
import com.bantads.ms_auth.exception.LoginJaCadastradoException;
import com.bantads.ms_auth.model.TipoUsuario;
import com.bantads.ms_auth.model.Usuario;
import com.bantads.ms_auth.repository.UsuarioRepository;

@Service
public class UsuarioService {

    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final GeradorSenha geradorSenha;

    private final String hashDescartavel;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder, GeradorSenha geradorSenha) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.geradorSenha = geradorSenha;
        this.hashDescartavel = passwordEncoder.encode("usuario-inexistente");
    }

    public Usuario autenticar(String login, String senha) {
        Optional<Usuario> encontrado = repository.findByLogin(login);
        String hashArmazenado = encontrado.map(Usuario::getSenhaHash).orElse(hashDescartavel);

        // o hash roda sempre, mesmo para inativo/inexistente, para nao vazar pelo tempo de resposta
        boolean senhaConfere = passwordEncoder.matches(senha, hashArmazenado);
        if (encontrado.isEmpty() || !senhaConfere || !encontrado.get().isAtivo()) {
            throw new CredenciaisInvalidasException();
        }
        return encontrado.get();
    }

    /**
     * Cria o registro do cliente aprovado (R9) e devolve a senha em claro, que so
     * existe no retorno: no banco fica apenas o hash.
     */
    public String criarCliente(String sagaId, String cpf, String login) {
        String senha = geradorSenha.gerar();
        criar(sagaId, cpf, TipoUsuario.CLIENTE, login, senha);
        return senha;
    }

    /**
     * Cria o registro do gerente inserido (R13) com a senha informada no formulario.
     */
    public void criarGerente(String sagaId, String cpf, String login, String senha) {
        criar(sagaId, cpf, TipoUsuario.GERENTE, login, senha);
    }

    private void criar(String sagaId, String cpf, TipoUsuario tipo, String login, String senha) {
        String cpfLimpo = normalizarCpf(cpf);
        String loginLimpo = login != null ? login.trim() : "";

        Optional<Usuario> mesmoCpf = repository.findByCpf(cpfLimpo);
        if (mesmoCpf.isPresent()) {
            Usuario existente = mesmoCpf.get();
            if (!sagaId.equals(existente.getSagaCriacao())) {
                throw new CpfJaCadastradoException();
            }
            // entrega anterior desta SAGA caiu antes de responder: regrava o hash com a senha atual
            existente.setSenhaHash(passwordEncoder.encode(senha));
            repository.save(existente);
            return;
        }
        if (repository.existsByCpf(cpfLimpo)) {
            throw new CpfJaCadastradoException();
        }
        if (repository.existsByLogin(loginLimpo) || repository.existsByLoginIgnoreCase(loginLimpo)) {
            throw new LoginJaCadastradoException(loginLimpo);
        }

        Usuario usuario = new Usuario(cpfLimpo, tipo, loginLimpo, passwordEncoder.encode(senha));
        usuario.setSagaCriacao(sagaId);
        try {
            repository.insert(usuario);
        } catch (DuplicateKeyException e) {
            // corrida com outra criacao: o indice unico e quem decide
            if (repository.existsByLogin(loginLimpo) || repository.existsByLoginIgnoreCase(loginLimpo)) {
                throw new LoginJaCadastradoException(loginLimpo);
            }
            throw new CpfJaCadastradoException();
        }
    }

    public void removerCriadoPelaSaga(String sagaId, String cpf) {
        String cpfLimpo = normalizarCpf(cpf);
        repository.findByCpf(cpfLimpo)
                .filter(usuario -> sagaId.equals(usuario.getSagaCriacao()))
                .ifPresent(repository::delete);
    }

    private String normalizarCpf(String cpf) {
        if (cpf == null) {
            return "";
        }
        String digitos = cpf.replaceAll("\\D", "");
        return digitos.isEmpty() ? cpf.trim() : digitos;
    }
}
