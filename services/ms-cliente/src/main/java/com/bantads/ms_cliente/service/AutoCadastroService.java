package com.bantads.ms_cliente.service;

import com.bantads.ms_cliente.exception.CadastroDuplicadoException;
import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import com.bantads.ms_cliente.repository.ClienteRepository;
import com.bantads.ms_cliente.repository.SolicitacaoCadastroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AutoCadastroService {

    private static final Logger log = LoggerFactory.getLogger(AutoCadastroService.class);

    public static final String FILA_EMAIL_CMD = "ms.email.cmd";
    public static final String TIPO_EMAIL_REJEICAO = "email.solicitacao-rejeitada";

    private final SolicitacaoCadastroRepository solicitacaoRepository;
    private final ClienteRepository clienteRepository;
    private final RabbitTemplate rabbitTemplate;

    public AutoCadastroService(
            SolicitacaoCadastroRepository solicitacaoRepository,
            ClienteRepository clienteRepository,
            RabbitTemplate rabbitTemplate
    ) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.clienteRepository = clienteRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    public SolicitacaoCadastro registrarSolicitacao(SolicitacaoCadastro solicitacao) {
        validarCampos(solicitacao);

        String cpf = solicitacao.getCpf();
        String email = solicitacao.getEmail();

        // Unicidade de CPF contra solicitações e contra clientes já cadastrados (R1)
        if (solicitacaoRepository.existsByCpf(cpf)) {
            throw new CadastroDuplicadoException("CPF já possui uma solicitação de cadastro.");
        }
        if (clienteRepository.existsById(cpf) || clienteRepository.existsByCpf(cpf)) {
            throw new CadastroDuplicadoException("CPF já cadastrado como cliente no sistema.");
        }

        // Unicidade de E-mail contra solicitações e contra clientes já cadastrados (R1)
        if (solicitacaoRepository.existsByEmail(email) || solicitacaoRepository.existsByEmailIgnoreCase(email)) {
            throw new CadastroDuplicadoException("E-mail já utilizado em outra solicitação.");
        }
        if (clienteRepository.existsByEmail(email) || clienteRepository.existsByEmailIgnoreCase(email)) {
            throw new CadastroDuplicadoException("E-mail já cadastrado como cliente no sistema.");
        }

        solicitacao.setStatus("PENDENTE");
        return solicitacaoRepository.save(solicitacao);
    }

    private void validarCampos(SolicitacaoCadastro solicitacao) {
        if (solicitacao == null) {
            throw new IllegalArgumentException("Dados da solicitação são obrigatórios.");
        }
        if (solicitacao.getNome() == null || solicitacao.getNome().trim().isBlank()) {
            throw new IllegalArgumentException("Nome é obrigatório.");
        }
        // Espaço nas pontas jogaria o cliente para o início da listagem ordenada por nome (R11, R16)
        solicitacao.setNome(solicitacao.getNome().trim());
        if (solicitacao.getCpf() == null || solicitacao.getCpf().trim().isBlank()) {
            throw new IllegalArgumentException("CPF é obrigatório.");
        }
        String cpfLimpo = solicitacao.getCpf().replaceAll("\\D", "");
        if (cpfLimpo.length() != 11) {
            throw new IllegalArgumentException("CPF deve conter 11 dígitos numéricos.");
        }
        solicitacao.setCpf(cpfLimpo);

        if (solicitacao.getEmail() == null || solicitacao.getEmail().trim().isBlank()) {
            throw new IllegalArgumentException("E-mail é obrigatório.");
        }
        String emailLimpo = solicitacao.getEmail().trim();
        if (!emailLimpo.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new IllegalArgumentException("E-mail inválido.");
        }
        solicitacao.setEmail(emailLimpo);

        if (solicitacao.getTelefone() == null || solicitacao.getTelefone().trim().isBlank()) {
            throw new IllegalArgumentException("Telefone é obrigatório.");
        }
        if (solicitacao.getSalario() == null || solicitacao.getSalario().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Salário deve ser maior que zero.");
        }
        if (solicitacao.getLogradouro() == null || solicitacao.getLogradouro().trim().isBlank()) {
            throw new IllegalArgumentException("Logradouro é obrigatório.");
        }
        if (solicitacao.getNumero() == null || solicitacao.getNumero().trim().isBlank()) {
            throw new IllegalArgumentException("Número é obrigatório.");
        }
        if (solicitacao.getCep() == null || solicitacao.getCep().trim().isBlank()) {
            throw new IllegalArgumentException("CEP é obrigatório.");
        }
        if (solicitacao.getCidade() == null || solicitacao.getCidade().trim().isBlank()) {
            throw new IllegalArgumentException("Cidade é obrigatória.");
        }
        if (solicitacao.getUf() == null || solicitacao.getUf().trim().isBlank()) {
            throw new IllegalArgumentException("UF é obrigatória.");
        }
    }

    public List<SolicitacaoCadastro> listarSolicitacoes(String status) {
        if (status != null && !status.isBlank()) {
            return solicitacaoRepository.findByStatusIgnoreCase(status.trim());
        }
        return solicitacaoRepository.findAll();
    }

    /**
     * R10: rejeição síncrona. Só solicitação Pendente pode ser rejeitada; o motivo é obrigatório.
     * O e-mail ao cliente é fire-and-forget: falha na publicação não desfaz a rejeição.
     */
    public SolicitacaoCadastro rejeitarSolicitacao(UUID id, String motivo) {
        if (motivo == null || motivo.trim().isBlank()) {
            throw new IllegalArgumentException("Motivo da rejeição é obrigatório.");
        }
        SolicitacaoCadastro solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Solicitação não encontrada."));
        if (!"PENDENTE".equalsIgnoreCase(solicitacao.getStatus())) {
            throw new IllegalStateException("Só é possível rejeitar uma solicitação pendente.");
        }

        solicitacao.setStatus("NAO_APROVADO");
        solicitacao.setMotivoRejeicao(motivo.trim());
        solicitacao.setDataAprovacaoRejeicao(LocalDateTime.now());
        SolicitacaoCadastro salva = solicitacaoRepository.save(solicitacao);

        publicarEmailRejeicao(salva);
        return salva;
    }

    // sem sagaId: a rejeição não é SAGA (enunciado 5.7, mensagens fora da SAGA)
    private void publicarEmailRejeicao(SolicitacaoCadastro solicitacao) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", solicitacao.getEmail());
        payload.put("nome", solicitacao.getNome());
        payload.put("motivo", solicitacao.getMotivoRejeicao());

        Map<String, Object> mensagem = new LinkedHashMap<>();
        mensagem.put("tipo", TIPO_EMAIL_REJEICAO);
        // formatter explícito: LocalDateTime.toString() omite os segundos quando são zero
        mensagem.put("timestamp", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)));
        mensagem.put("payload", payload);

        try {
            rabbitTemplate.convertAndSend(FILA_EMAIL_CMD, mensagem);
        } catch (RuntimeException e) {
            log.warn("E-mail de rejeição não publicado para a solicitação {}: {}", solicitacao.getId(), e.getMessage());
        }
    }
}
