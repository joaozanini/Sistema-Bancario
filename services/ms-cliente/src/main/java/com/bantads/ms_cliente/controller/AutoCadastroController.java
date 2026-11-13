package com.bantads.ms_cliente.controller;

import com.bantads.ms_cliente.dto.EnderecoDTO;
import com.bantads.ms_cliente.dto.SolicitacaoCadastroDTO;
import com.bantads.ms_cliente.dto.SolicitacoesListDTO;
import com.bantads.ms_cliente.exception.CadastroDuplicadoException;
import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import com.bantads.ms_cliente.model.StatusSolicitacao;
import com.bantads.ms_cliente.service.AutoCadastroService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/solicitacoes")
public class AutoCadastroController {

    private static final String SOLICITACOES_URL = "http://localhost:8082/solicitacoes";
    private static final String CLIENTES_URL = "http://localhost:8082/clientes";

    // Mesmo formato dos valores do MS Conta: até 15 dígitos inteiros e 2 casas, o que cabe no NUMERIC(19, 4)
    // e barra a notação científica ("1E-99999999" passa no new BigDecimal() e trava a thread na conversão)
    private static final Pattern FORMATO_SALARIO = Pattern.compile("\\d{1,15}(\\.\\d{1,2})?");

    private final AutoCadastroService service;

    public AutoCadastroController(AutoCadastroService service) {
        this.service = service;
    }

    /**
     * Requisito R1: autocadastro. Grava a solicitação como PENDENTE e responde 201 com ela.
     */
    @PostMapping
    public ResponseEntity<?> criarSolicitacao(@RequestBody(required = false) SolicitacaoCadastroDTO dto) {
        try {
            if (dto == null) {
                return ResponseEntity.badRequest().body(Map.of("erro", "Dados da solicitação não informados."));
            }

            BigDecimal salario = null;
            if (dto.getSalario() != null && !dto.getSalario().isBlank()) {
                String salarioInformado = dto.getSalario().trim();
                if (!FORMATO_SALARIO.matcher(salarioInformado).matches()) {
                    return ResponseEntity.badRequest().body(Map.of("erro", "Salário inválido."));
                }
                salario = new BigDecimal(salarioInformado);
            }

            // Mapeando DTO de entrada para a Entidade; sem "endereco" os campos ficam nulos e a validação recusa
            EnderecoDTO endereco = dto.getEndereco() != null
                    ? dto.getEndereco()
                    : new EnderecoDTO(null, null, null, null, null, null);

            SolicitacaoCadastro entidade = new SolicitacaoCadastro();
            entidade.setNome(dto.getNome());
            entidade.setCpf(dto.getCpf());
            entidade.setEmail(dto.getEmail());
            entidade.setTelefone(dto.getTelefone());
            entidade.setSalario(salario);
            entidade.setLogradouro(endereco.logradouro());
            entidade.setNumero(endereco.numero());
            entidade.setComplemento(endereco.complemento());
            entidade.setCep(endereco.cep());
            entidade.setCidade(endereco.cidade());
            entidade.setUf(endereco.uf());

            // Regra de validação e unicidade
            SolicitacaoCadastro salva = service.registrarSolicitacao(entidade);

            return ResponseEntity
                    .created(URI.create("/solicitacoes/" + salva.getCpf()))
                    .body(converterParaDTO(salva));
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
     * Aprovada, Não aprovada).
     */
    @GetMapping
    public ResponseEntity<?> listarSolicitacoes(
            @RequestParam(value = "status", required = false) String status
    ) {
        try {
            List<SolicitacaoCadastroDTO> dtos = service.listarSolicitacoes(status).stream()
                    .map(this::converterParaDTO)
                    .toList();
            return ResponseEntity.ok(new SolicitacoesListDTO(dtos, Map.of("self", Map.of("href", SOLICITACOES_URL))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Requisito R8: consulta de uma solicitação pelo CPF.
     */
    @GetMapping("/{cpf}")
    public ResponseEntity<?> buscarSolicitacao(@PathVariable("cpf") String cpf) {
        try {
            return ResponseEntity.ok(converterParaDTO(service.buscarPorCpf(cpf)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Requisito R10: rejeitar solicitação pendente informando o motivo.
     * Síncrono (200).
     */
    @PostMapping("/{cpf}/rejeicao")
    public ResponseEntity<?> rejeitarSolicitacao(
            @PathVariable("cpf") String cpf,
            @RequestBody(required = false) Map<String, String> corpo
    ) {
        try {
            String motivo = corpo == null ? null : corpo.get("motivo");
            SolicitacaoCadastro rejeitada = service.rejeitarSolicitacao(cpf, motivo);
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
        dto.setCpf(entidade.getCpf());
        dto.setNome(entidade.getNome());
        dto.setEmail(entidade.getEmail());
        dto.setTelefone(entidade.getTelefone());
        if (entidade.getSalario() != null) {
            // 2 casas na API; o banco guarda NUMERIC(19, 4) e devolveria "4500.0000"
            dto.setSalario(entidade.getSalario().setScale(2, RoundingMode.HALF_EVEN).toPlainString());
        }
        dto.setEndereco(new EnderecoDTO(
                entidade.getLogradouro(),
                entidade.getNumero(),
                entidade.getComplemento(),
                entidade.getCep(),
                entidade.getCidade(),
                entidade.getUf()));
        dto.setStatus(entidade.getStatus());
        dto.setMotivo(entidade.getMotivoRejeicao());
        dto.setDataHoraProcessamento(entidade.getDataAprovacaoRejeicao());

        // HATEOAS (enunciado 5.6): URL interna do MS; o Gateway reescreve o host para o dele.
        // Os links seguem o estado: só a solicitação PENDENTE pode ser aprovada ou rejeitada.
        String self = SOLICITACOES_URL + "/" + entidade.getCpf();
        dto.addLink("self", self);
        if (StatusSolicitacao.PENDENTE.equalsIgnoreCase(entidade.getStatus())) {
            dto.addLink("aprovacao", self + "/aprovacao");
            dto.addLink("rejeicao", self + "/rejeicao");
        } else if (StatusSolicitacao.APROVADA.equalsIgnoreCase(entidade.getStatus())) {
            dto.addLink("cliente", CLIENTES_URL + "/" + entidade.getCpf());
        }
        return dto;
    }
}
