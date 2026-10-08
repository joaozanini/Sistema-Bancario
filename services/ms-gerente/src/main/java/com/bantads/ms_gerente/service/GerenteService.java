package com.bantads.ms_gerente.service;

import com.bantads.ms_gerente.dto.AtualizacaoGerenteDTO;
import com.bantads.ms_gerente.dto.GerenteDTO;
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

        gerente.setNome(dados.nome());
        gerente.setTelefone(dados.telefone());

        return GerenteDTO.de(repository.save(gerente));
    }
}
