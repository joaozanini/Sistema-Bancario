package com.bantads.ms_cliente.service;

import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import com.bantads.ms_cliente.repository.SolicitacaoCadastroRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutoCadastroService {

    private final SolicitacaoCadastroRepository repository;

    public AutoCadastroService(SolicitacaoCadastroRepository repository) {
        this.repository = repository;
    }

    public SolicitacaoCadastro registrarSolicitacao(SolicitacaoCadastro solicitacao) {
        if (repository.existsByCpf(solicitacao.getCpf())) {
            throw new IllegalArgumentException("CPF já possui uma solicitação de cadastro.");
        }
        if (repository.existsByEmail(solicitacao.getEmail())) {
            throw new IllegalArgumentException("E-mail já utilizado em outra solicitação.");
        }
        
        solicitacao.setStatus("PENDENTE");
        return repository.save(solicitacao);
    }

    public List<SolicitacaoCadastro> listarSolicitacoes(String status) {
        if (status != null && !status.isBlank()) {
            return repository.findByStatusIgnoreCase(status.trim());
        }
        return repository.findAll();
    }
}