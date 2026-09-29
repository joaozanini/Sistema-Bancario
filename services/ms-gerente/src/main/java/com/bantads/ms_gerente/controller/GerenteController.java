package com.bantads.ms_gerente.controller;

import com.bantads.ms_gerente.dto.GerenteDTO;
import com.bantads.ms_gerente.repository.GerenteRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gerentes")
public class GerenteController {

    private final GerenteRepository repository;

    public GerenteController(GerenteRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{cpf}")
    public ResponseEntity<GerenteDTO> buscarPorCpf(@PathVariable String cpf) {
        return repository.findById(cpf)
                .map(GerenteDTO::de)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
