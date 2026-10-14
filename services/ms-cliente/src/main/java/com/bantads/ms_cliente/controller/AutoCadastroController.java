package com.bantads.ms_cliente.controller;

import com.bantads.ms_cliente.dto.SolicitacaoCadastroDTO;
import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import com.bantads.ms_cliente.service.AutoCadastroService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/solicitacoes")
public class AutoCadastroController {

    private final AutoCadastroService service;

    public AutoCadastroController(AutoCadastroService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> criarSolicitacao(@RequestBody SolicitacaoCadastroDTO dto) {
        try {
            // Mapeando DTO de entrada para a Entidade
            SolicitacaoCadastro entidade = new SolicitacaoCadastro();
            entidade.setNome(dto.getNome());
            entidade.setCpf(dto.getCpf());
            entidade.setEmail(dto.getEmail());
            entidade.setTelefone(dto.getTelefone());
            entidade.setSalario(new BigDecimal(dto.getSalario())); 
            entidade.setLogradouro(dto.getLogradouro());
            entidade.setNumero(dto.getNumero());
            entidade.setComplemento(dto.getComplemento());
            entidade.setCep(dto.getCep());
            entidade.setCidade(dto.getCidade());
            entidade.setUf(dto.getUf());

            // Regra de unicidade
            SolicitacaoCadastro salva = service.registrarSolicitacao(entidade);

            // Mapeando Entidade salva para DTO de saída
            SolicitacaoCadastroDTO respostaDto = converterParaDTO(salva);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensagem", "Solicitação enviada com sucesso.",
                "solicitacao", respostaDto
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Requisito R8: Listagem de solicitações de autocadastro (Pendente, Aprovado, Não aprovado).
     */
    @GetMapping
    public ResponseEntity<List<SolicitacaoCadastroDTO>> listarSolicitacoes(
            @RequestParam(value = "status", required = false) String status
    ) {
        List<SolicitacaoCadastro> lista = service.listarSolicitacoes(status);
        List<SolicitacaoCadastroDTO> dtos = lista.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    private SolicitacaoCadastroDTO converterParaDTO(SolicitacaoCadastro entidade) {
        SolicitacaoCadastroDTO dto = new SolicitacaoCadastroDTO();
        dto.setId(entidade.getId());
        dto.setNome(entidade.getNome());
        dto.setCpf(entidade.getCpf());
        dto.setEmail(entidade.getEmail());
        dto.setTelefone(entidade.getTelefone());
        if (entidade.getSalario() != null) {
            dto.setSalario(entidade.getSalario().toString());
        }
        dto.setLogradouro(entidade.getLogradouro());
        dto.setNumero(entidade.getNumero());
        dto.setComplemento(entidade.getComplemento());
        dto.setCep(entidade.getCep());
        dto.setCidade(entidade.getCidade());
        dto.setUf(entidade.getUf());
        dto.setStatus(entidade.getStatus());
        dto.setMotivoRejeicao(entidade.getMotivoRejeicao());
        dto.setDataAprovacaoRejeicao(entidade.getDataAprovacaoRejeicao());

        // Adiciona links HATEOAS
        dto.addLink("self", "http://localhost:8081/solicitacoes/" + entidade.getId());
        return dto;
    }
}