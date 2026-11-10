package com.bantads.ms_cliente.controller;

import com.bantads.ms_cliente.dto.SolicitacaoCadastroDTO;
import com.bantads.ms_cliente.exception.CadastroDuplicadoException;
import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import com.bantads.ms_cliente.service.AutoCadastroService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/solicitacoes")
public class AutoCadastroController {

    private static final String SOLICITACOES_URL = "http://localhost:8082/solicitacoes";
    private static final String CLIENTES_URL = "http://localhost:8082/clientes";

    private final AutoCadastroService service;

    public AutoCadastroController(AutoCadastroService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<?> criarSolicitacao(@RequestBody SolicitacaoCadastroDTO dto) {
        try {
            if (dto == null) {
                return ResponseEntity.badRequest().body(Map.of("erro", "Dados da solicitação não informados."));
            }

            BigDecimal salario = null;
            if (dto.getSalario() != null && !dto.getSalario().trim().isBlank()) {
                try {
                    salario = new BigDecimal(dto.getSalario().trim());
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(Map.of("erro", "Salário inválido."));
                }
            }

            // Mapeando DTO de entrada para a Entidade
            SolicitacaoCadastro entidade = new SolicitacaoCadastro();
            entidade.setNome(dto.getNome());
            entidade.setCpf(dto.getCpf());
            entidade.setEmail(dto.getEmail());
            entidade.setTelefone(dto.getTelefone());
            entidade.setSalario(salario);
            entidade.setLogradouro(dto.getLogradouro());
            entidade.setNumero(dto.getNumero());
            entidade.setComplemento(dto.getComplemento());
            entidade.setCep(dto.getCep());
            entidade.setCidade(dto.getCidade());
            entidade.setUf(dto.getUf());

            // Regra de validação e unicidade
            SolicitacaoCadastro salva = service.registrarSolicitacao(entidade);

            // Mapeando Entidade salva para DTO de saída
            SolicitacaoCadastroDTO respostaDto = converterParaDTO(salva);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "mensagem", "Solicitação enviada com sucesso.",
                    "solicitacao", respostaDto
            ));
        } catch (CadastroDuplicadoException e) {
            // R1: CPF ou e-mail repetido é conflito com um cadastro existente, não dado malformado
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            // Dois autocadastros simultâneos passam pela checagem acima; o índice único barra o segundo
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", "CPF ou e-mail já cadastrado no sistema."));
        }
    }

    /**
     * Requisito R8: Listagem de solicitações de autocadastro (Pendente,
     * Aprovado, Não aprovado).
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

    /**
     * Requisito R10: rejeitar solicitação pendente informando o motivo.
     * Síncrono (200).
     */
    @PostMapping("/{id}/rejeitar")
    public ResponseEntity<?> rejeitarSolicitacao(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) Map<String, String> corpo
    ) {
        try {
            String motivo = corpo == null ? null : corpo.get("motivo");
            SolicitacaoCadastro rejeitada = service.rejeitarSolicitacao(id, motivo);
            return ResponseEntity.ok(converterParaDTO(rejeitada));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", e.getMessage()));
        }
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

        // HATEOAS (enunciado 5.6): URL interna do MS; o Gateway reescreve o host para o dele.
        // Não há GET por id de solicitação, então o item aponta para a coleção e para as ações possíveis.
        dto.addLink("solicitacoes", SOLICITACOES_URL);
        if ("PENDENTE".equalsIgnoreCase(entidade.getStatus())) {
            dto.addLink("aprovar", SOLICITACOES_URL + "/" + entidade.getCpf() + "/aprovar");
            dto.addLink("rejeitar", SOLICITACOES_URL + "/" + entidade.getId() + "/rejeitar");
        } else if ("APROVADO".equalsIgnoreCase(entidade.getStatus())) {
            dto.addLink("cliente", CLIENTES_URL + "/" + entidade.getCpf());
        }
        return dto;
    }
}
