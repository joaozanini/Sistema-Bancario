package com.bantads.ms_gerente.service;

import com.bantads.ms_gerente.dto.AtualizacaoGerenteDTO;
import com.bantads.ms_gerente.dto.GerenteDTO;
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
    public GerenteDTO atualizar(String cpf, AtualizacaoGerenteDTO dados) {
        Gerente gerente = repository.findById(cpf)
                .orElseThrow(() -> new GerenteNaoEncontradoException("Gerente não encontrado."));

        if (repository.existsByEmailAndCpfNot(dados.email(), cpf)) {
            throw new EmailJaCadastradoException("E-mail já cadastrado para outro gerente.");
        }

        gerente.setNome(dados.nome());
        gerente.setEmail(dados.email());
        gerente.setTelefone(dados.telefone());

        return GerenteDTO.de(repository.save(gerente));
    }
}
