package com.bantads.ms_cliente.controller;

import com.bantads.ms_cliente.dto.SolicitacaoCadastroDTO;
import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import com.bantads.ms_cliente.service.AutoCadastroService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

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
            SolicitacaoCadastroDTO respostaDto = new SolicitacaoCadastroDTO();
            respostaDto.setId(salva.getId());
            respostaDto.setNome(salva.getNome());
            respostaDto.setCpf(salva.getCpf());
            respostaDto.setEmail(salva.getEmail());
            respostaDto.setTelefone(salva.getTelefone());
            respostaDto.setSalario(salva.getSalario().toString()); 
            respostaDto.setLogradouro(salva.getLogradouro());
            respostaDto.setNumero(salva.getNumero());
            respostaDto.setComplemento(salva.getComplemento());
            respostaDto.setCep(salva.getCep());
            respostaDto.setCidade(salva.getCidade());
            respostaDto.setUf(salva.getUf());
            respostaDto.setStatus(salva.getStatus());
            
            // Adiciona links HATEOAS no formato exigido[cite: 1]
            respostaDto.addLink("self", "http://localhost:8081/solicitacoes/" + salva.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensagem", "Solicitação enviada com sucesso.",
                "solicitacao", respostaDto
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }
}