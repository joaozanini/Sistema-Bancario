package com.bantads.ms_conta.controller;

import com.bantads.ms_conta.dto.ContaDTO;
import com.bantads.ms_conta.dto.ExtratoResponseDTO;
import com.bantads.ms_conta.dto.MovimentacaoRequestDTO;
import com.bantads.ms_conta.dto.MovimentacaoResponseDTO;
import com.bantads.ms_conta.dto.TransferenciaRequestDTO;
import com.bantads.ms_conta.service.ContaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contas")
public class ContaController {

    private final ContaService service;

    public ContaController(ContaService service) {
        this.service = service;
    }

    @PostMapping("/{numero}/transferencia")
    public ResponseEntity<MovimentacaoResponseDTO> transferir(
            @PathVariable("numero") String numeroContaOrigem,
            @RequestBody TransferenciaRequestDTO dto,
            @RequestHeader(value = "X-User-CPF", required = false) String xUserCpf
    ) {
        MovimentacaoResponseDTO resposta = service.transferir(numeroContaOrigem, dto, xUserCpf);
        // 201, como saque e depósito: a transferência cria os eventos TransferênciaOrigem/Destino
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    @PostMapping("/{numero}/saque")
    public ResponseEntity<MovimentacaoResponseDTO> sacar(
            @PathVariable("numero") String numeroConta,
            @RequestBody MovimentacaoRequestDTO dto,
            @RequestHeader(value = "X-User-CPF", required = false) String xUserCpf
    ) {
        MovimentacaoResponseDTO resposta = service.sacar(numeroConta, dto, xUserCpf);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    @PostMapping("/{numero}/deposito")
    public ResponseEntity<MovimentacaoResponseDTO> depositar(
            @PathVariable("numero") String numeroConta,
            @RequestBody MovimentacaoRequestDTO dto,
            @RequestHeader(value = "X-User-CPF", required = false) String xUserCpf
    ) {
        MovimentacaoResponseDTO resposta = service.depositar(numeroConta, dto, xUserCpf);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    @GetMapping("/{numero}/extrato")
    public ResponseEntity<ExtratoResponseDTO> consultarExtratoPorConta(
            @PathVariable("numero") String numeroConta,
            @RequestParam(value = "inicio", required = false) String inicio,
            @RequestParam(value = "fim", required = false) String fim,
            @RequestHeader(value = "X-User-CPF", required = false) String xUserCpf,
            @RequestHeader(value = "X-User-Tipo", required = false) String xUserTipo
    ) {
        ExtratoResponseDTO resposta = service.consultarExtrato(numeroConta, inicio, fim, xUserCpf, xUserTipo);
        return ResponseEntity.ok(resposta);
    }

    // Uso interno do Gateway nas composições (R11, R12, R16); não é exposto ao front
    @GetMapping
    public ResponseEntity<List<ContaDTO>> listarContas() {
        List<ContaDTO> contas = service.listarContas();
        return ResponseEntity.ok(contas);
    }

    @GetMapping("/{numero}")
    public ResponseEntity<ContaDTO> buscarContaPorNumero(
            @PathVariable("numero") String numeroConta,
            @RequestHeader(value = "X-User-CPF", required = false) String xUserCpf,
            @RequestHeader(value = "X-User-Tipo", required = false) String xUserTipo
    ) {
        ContaDTO dto = service.buscarContaPorNumero(numeroConta, xUserCpf, xUserTipo);
        return ResponseEntity.ok(dto);
    }
}
