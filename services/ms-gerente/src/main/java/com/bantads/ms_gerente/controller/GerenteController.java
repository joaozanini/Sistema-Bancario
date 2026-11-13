package com.bantads.ms_gerente.controller;

import com.bantads.ms_gerente.dto.AtualizacaoGerenteDTO;
import com.bantads.ms_gerente.dto.GerenteDTO;
import com.bantads.ms_gerente.service.GerenteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/gerentes")
public class GerenteController {

    private final GerenteService service;

    public GerenteController(GerenteService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<GerenteDTO>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/{cpf}")
    public ResponseEntity<GerenteDTO> buscarPorCpf(@PathVariable String cpf) {
        return service.buscarPorCpf(cpf)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{cpf}")
    public ResponseEntity<GerenteDTO> atualizar(@PathVariable String cpf,
                                                @Valid @RequestBody AtualizacaoGerenteDTO dados) {
        return ResponseEntity.ok(service.atualizar(cpf, dados));
    }
}
