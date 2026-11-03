package com.bantads.ms_gerente.service;

import com.bantads.ms_gerente.dto.AtualizacaoGerenteDTO;
import com.bantads.ms_gerente.dto.GerenteDTO;
import com.bantads.ms_gerente.exception.CpfJaCadastradoException;
import com.bantads.ms_gerente.exception.EmailJaCadastradoException;
import com.bantads.ms_gerente.exception.GerenteNaoEncontradoException;
import com.bantads.ms_gerente.model.Gerente;
import com.bantads.ms_gerente.repository.GerenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GerenteService {

    private final GerenteRepository repository;

    public GerenteService(GerenteRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public GerenteDTO inserir(Gerente novoGerente) {
        if (novoGerente == null) {
            throw new IllegalArgumentException("Dados do gerente são obrigatórios.");
        }
        String cpfLimpo = normalizarCpf(novoGerente.getCpf());
        String emailLimpo = normalizarEmail(novoGerente.getEmail());

        if (novoGerente.getNome() == null || novoGerente.getNome().isBlank()) {
            throw new IllegalArgumentException("Nome é obrigatório.");
        }

        validarUnicidadeCriacao(cpfLimpo, emailLimpo);

        novoGerente.setCpf(cpfLimpo);
        novoGerente.setEmail(emailLimpo);
        novoGerente.setNome(novoGerente.getNome().trim());
        novoGerente.setAtivo(true);

        return GerenteDTO.de(repository.save(novoGerente));
    }

    @Transactional(readOnly = true)
    public void validarUnicidadeCriacao(String cpf, String email) {
        String cpfLimpo = normalizarCpf(cpf);
        String emailLimpo = normalizarEmail(email);

        if (repository.existsById(cpfLimpo) || repository.existsByCpf(cpfLimpo)) {
            throw new CpfJaCadastradoException("CPF já cadastrado para outro gerente.");
        }
        if (repository.existsByEmail(emailLimpo) || repository.existsByEmailIgnoreCase(emailLimpo)) {
            throw new EmailJaCadastradoException("E-mail já cadastrado para outro gerente.");
        }
    }

    @Transactional
    public GerenteDTO atualizar(String cpf, AtualizacaoGerenteDTO dados) {
        Gerente gerente = repository.findById(cpf)
                .orElseThrow(() -> new GerenteNaoEncontradoException("Gerente não encontrado."));

        gerente.setNome(dados.nome());
        gerente.setTelefone(dados.telefone());

        return GerenteDTO.de(repository.save(gerente));
    }

    private String normalizarCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            throw new IllegalArgumentException("CPF é obrigatório.");
        }
        String cpfLimpo = cpf.replaceAll("\\D", "");
        if (cpfLimpo.length() != 11) {
            throw new IllegalArgumentException("CPF deve conter 11 dígitos numéricos.");
        }
        return cpfLimpo;
    }

    private String normalizarEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("E-mail é obrigatório.");
        }
        String emailLimpo = email.trim();
        if (!emailLimpo.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
        return emailLimpo;
    }
}
