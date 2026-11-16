package com.bantads.ms_gerente.service;

import com.bantads.ms_gerente.dto.AtualizacaoGerenteDTO;
import com.bantads.ms_gerente.dto.GerenteDTO;
import com.bantads.ms_gerente.exception.CpfJaCadastradoException;
import com.bantads.ms_gerente.exception.EmailJaCadastradoException;
import com.bantads.ms_gerente.exception.GerenteNaoEncontradoException;
import com.bantads.ms_gerente.exception.UltimoGerenteAtivoException;
import com.bantads.ms_gerente.model.Gerente;
import com.bantads.ms_gerente.repository.GerenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class GerenteService {

    // Tamanho das colunas da tabela gerente
    private static final int TAMANHO_NOME = 100;
    private static final int TAMANHO_EMAIL = 100;
    private static final int TAMANHO_TELEFONE = 20;

    private final GerenteRepository repository;

    public GerenteService(GerenteRepository repository) {
        this.repository = repository;
    }

    // R12: lista todos os gerentes
    @Transactional(readOnly = true)
    public List<GerenteDTO> listar() {
        return repository.findAll().stream()
                .map(GerenteDTO::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<GerenteDTO> buscarPorCpf(String cpf) {
        return repository.findById(cpf).map(GerenteDTO::de);
    }

    // R9: lista os gerentes ativos
    @Transactional(readOnly = true)
    public List<String> listarCpfsAtivos() {
        return repository.findByAtivoTrueOrderByCpf().stream()
                .map(Gerente::getCpf)
                .toList();
    }

    // Compensação de R13: remove o gerente
    @Transactional
    public void remover(String cpf) {
        repository.deleteById(cpf);
    }

    // R15: inativa o gerente, exceto o último ativo
    @Transactional
    public void inativar(String cpf) {
        Gerente gerente = repository.findById(cpf)
                .orElseThrow(() -> new GerenteNaoEncontradoException("Gerente não encontrado."));
        // Gerente já inativo não é inativado de novo
        if (!gerente.isAtivo()) {
            throw new GerenteNaoEncontradoException("Gerente não encontrado.");
        }
        if (repository.countByAtivoTrue() <= 1) {
            throw new UltimoGerenteAtivoException("Não é permitido remover o último gerente ativo do banco.");
        }
        gerente.setAtivo(false);
        repository.save(gerente);
    }

    // Compensação de R15: reativa o gerente
    @Transactional
    public void reativar(String cpf) {
        repository.findById(cpf).ifPresent(gerente -> {
            gerente.setAtivo(true);
            repository.save(gerente);
        });
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
        String nome = validarTamanho("Nome", novoGerente.getNome().trim(), TAMANHO_NOME);
        String telefone = novoGerente.getTelefone() == null ? null
                : validarTamanho("Telefone", novoGerente.getTelefone().trim(), TAMANHO_TELEFONE);

        validarUnicidadeCriacao(cpfLimpo, emailLimpo);

        novoGerente.setCpf(cpfLimpo);
        novoGerente.setEmail(emailLimpo);
        novoGerente.setNome(nome);
        novoGerente.setTelefone(telefone);
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

        // R14: e-mail e CPF não podem mudar
        if (dados.email() != null && !dados.email().trim().equalsIgnoreCase(gerente.getEmail())) {
            throw new IllegalArgumentException("O e-mail é o login do gerente e não pode ser alterado.");
        }
        if (dados.cpf() != null && !dados.cpf().replaceAll("\\D", "").equals(gerente.getCpf())) {
            throw new IllegalArgumentException("O CPF do gerente não pode ser alterado.");
        }

        gerente.setNome(validarTamanho("Nome", dados.nome().trim(), TAMANHO_NOME));
        gerente.setTelefone(dados.telefone() == null ? null
                : validarTamanho("Telefone", dados.telefone().trim(), TAMANHO_TELEFONE));

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
        return validarTamanho("E-mail", emailLimpo, TAMANHO_EMAIL);
    }

    // Recusa valor maior que o tamanho da coluna
    private String validarTamanho(String campo, String valor, int maximo) {
        if (valor.length() > maximo) {
            throw new IllegalArgumentException(campo + " deve ter no máximo " + maximo + " caracteres.");
        }
        return valor;
    }
}
