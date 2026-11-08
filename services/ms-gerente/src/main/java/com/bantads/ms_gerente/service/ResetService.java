package com.bantads.ms_gerente.service;

import com.bantads.ms_gerente.model.Gerente;
import com.bantads.ms_gerente.repository.GerenteRepository;
import com.bantads.ms_gerente.repository.SagaIdempotenciaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResetService {

    private final GerenteRepository gerenteRepository;
    private final SagaIdempotenciaRepository idempotenciaRepository;

    public ResetService(GerenteRepository gerenteRepository, SagaIdempotenciaRepository idempotenciaRepository) {
        this.gerenteRepository = gerenteRepository;
        this.idempotenciaRepository = idempotenciaRepository;
    }

    // Gerentes pre-cadastrados (enunciado, secao 4)
    @Transactional
    public void reset() {
        idempotenciaRepository.deleteAllInBatch();
        gerenteRepository.deleteAllInBatch();
        gerenteRepository.saveAll(List.of(
                gerente("98574307084", "Geniéve", "ger1@bantads.com.br", "41999990001"),
                gerente("64065268052", "Godophredo", "ger2@bantads.com.br", "41999990002"),
                gerente("23862179060", "Gyândula", "ger3@bantads.com.br", "41999990003"),
                gerente("40501740066", "Gadamântio", "ger4@bantads.com.br", "41999990004")
        ));
    }

    private Gerente gerente(String cpf, String nome, String email, String telefone) {
        Gerente gerente = new Gerente();
        gerente.setCpf(cpf);
        gerente.setNome(nome);
        gerente.setEmail(email);
        gerente.setTelefone(telefone);
        gerente.setAtivo(true);
        return gerente;
    }
}
