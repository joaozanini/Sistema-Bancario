package com.bantads.ms_conta.controller;

import com.bantads.ms_conta.dto.ContaDTO;
import com.bantads.ms_conta.dto.TransferenciaRequestDTO;
import com.bantads.ms_conta.dto.TransferenciaResponseDTO;
import com.bantads.ms_conta.service.ContaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contas")
public class ContaController {

    private final ContaService service;

    public ContaController(ContaService service) {
        this.service = service;
    }

    @PostMapping("/{numero}/transferencia")
    public ResponseEntity<TransferenciaResponseDTO> transferir(
            @PathVariable("numero") String numeroContaOrigem,
            @RequestBody TransferenciaRequestDTO dto,
            @RequestHeader(value = "X-User-CPF", required = false) String xUserCpf
    ) {
        TransferenciaResponseDTO resposta = service.transferir(numeroContaOrigem, dto, xUserCpf);
        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/{numero}")
    public ResponseEntity<ContaDTO> buscarContaPorNumero(@PathVariable("numero") String numeroConta) {
        ContaDTO dto = service.buscarContaPorNumero(numeroConta);
        return ResponseEntity.ok(dto);
    }
}