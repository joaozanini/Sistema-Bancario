package com.bantads.ms_conta.controller;

import com.bantads.ms_conta.dto.ContaDTO;
import com.bantads.ms_conta.service.ContaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Requisito R3: a conta é um sub-recurso do cliente (cada cliente tem exatamente uma).
 * É por aqui que a tela inicial do cliente descobre o número da conta e o saldo.
 */
@RestController
public class ContaDoClienteController {

    private final ContaService service;

    public ContaDoClienteController(ContaService service) {
        this.service = service;
    }

    @GetMapping("/clientes/{cpf}/conta")
    public ResponseEntity<ContaDTO> buscarContaDoCliente(
            @PathVariable("cpf") String cpf,
            @RequestHeader(value = "X-User-CPF", required = false) String xUserCpf,
            @RequestHeader(value = "X-User-Tipo", required = false) String xUserTipo
    ) {
        return ResponseEntity.ok(service.buscarContaDoCliente(cpf, xUserCpf, xUserTipo));
    }
}
