package com.bantads.ms_cliente.service;

import com.bantads.ms_cliente.exception.CadastroDuplicadoException;
import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import com.bantads.ms_cliente.model.StatusSolicitacao;
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

        solicitacao.setStatus(StatusSolicitacao.PENDENTE);
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
        exigirTamanhoMaximo(solicitacao.getNome(), 100, "Nome");
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
        exigirTamanhoMaximo(emailLimpo, 100, "E-mail");
        solicitacao.setEmail(emailLimpo);

        if (solicitacao.getTelefone() == null || solicitacao.getTelefone().trim().isBlank()) {
            throw new IllegalArgumentException("Telefone é obrigatório.");
        }
        solicitacao.setTelefone(solicitacao.getTelefone().trim());
        exigirTamanhoMaximo(solicitacao.getTelefone(), 20, "Telefone");
        if (solicitacao.getSalario() == null || solicitacao.getSalario().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Salário deve ser maior que zero.");
        }
        if (solicitacao.getLogradouro() == null || solicitacao.getLogradouro().trim().isBlank()) {
            throw new IllegalArgumentException("Logradouro é obrigatório.");
        }
        solicitacao.setLogradouro(solicitacao.getLogradouro().trim());
        exigirTamanhoMaximo(solicitacao.getLogradouro(), 150, "Logradouro");

        if (solicitacao.getNumero() == null || solicitacao.getNumero().trim().isBlank()) {
            throw new IllegalArgumentException("Número é obrigatório.");
        }
        solicitacao.setNumero(solicitacao.getNumero().trim());
        exigirTamanhoMaximo(solicitacao.getNumero(), 10, "Número");

        // Complemento é opcional: em branco vira null, como no contrato
        String complemento = solicitacao.getComplemento() == null ? "" : solicitacao.getComplemento().trim();
        exigirTamanhoMaximo(complemento, 100, "Complemento");
        solicitacao.setComplemento(complemento.isEmpty() ? null : complemento);

        if (solicitacao.getCep() == null || solicitacao.getCep().trim().isBlank()) {
            throw new IllegalArgumentException("CEP é obrigatório.");
        }
        // O contrato usa o CEP só com dígitos; a máscara 80000-000 do formulário é aceita e removida
        String cepLimpo = solicitacao.getCep().replaceAll("\\D", "");
        if (cepLimpo.length() != 8) {
            throw new IllegalArgumentException("CEP deve conter 8 dígitos numéricos.");
        }
        solicitacao.setCep(cepLimpo);

        if (solicitacao.getCidade() == null || solicitacao.getCidade().trim().isBlank()) {
            throw new IllegalArgumentException("Cidade é obrigatória.");
        }
        solicitacao.setCidade(solicitacao.getCidade().trim());
        exigirTamanhoMaximo(solicitacao.getCidade(), 100, "Cidade");

        if (solicitacao.getUf() == null || solicitacao.getUf().trim().isBlank()) {
            throw new IllegalArgumentException("UF é obrigatória.");
        }
        String uf = solicitacao.getUf().trim().toUpperCase();
        if (!uf.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("UF deve ter 2 letras.");
        }
        solicitacao.setUf(uf);
    }

    // Sem esta checagem o texto longo só falharia no banco, e o erro de tamanho viraria um 409 de duplicidade
    private void exigirTamanhoMaximo(String valor, int maximo, String campo) {
        if (valor != null && valor.length() > maximo) {
            throw new IllegalArgumentException(campo + " deve ter no máximo " + maximo + " caracteres.");
        }
    }

    public List<SolicitacaoCadastro> listarSolicitacoes(String status) {
        if (status == null || status.isBlank()) {
            return solicitacaoRepository.findAll();
        }
        String filtro = StatusSolicitacao.normalizar(status);
        if (filtro == null) {
            throw new IllegalArgumentException("Status inválido. Use PENDENTE, APROVADA ou NAO_APROVADA.");
        }
        return solicitacaoRepository.findByStatusIgnoreCase(filtro);
    }

    // R8: consulta de uma solicitação pelo CPF, que é a chave usada nas rotas de aprovação e rejeição
    public SolicitacaoCadastro buscarPorCpf(String cpf) {
        String cpfLimpo = cpf == null ? "" : cpf.replaceAll("\\D", "");
        return solicitacaoRepository.findByCpf(cpfLimpo)
                .orElseThrow(() -> new NoSuchElementException("Solicitação não encontrada."));
    }

    /**
     * R10: rejeição síncrona. Só solicitação Pendente pode ser rejeitada; o motivo é obrigatório.
     * O e-mail ao cliente é fire-and-forget: falha na publicação não desfaz a rejeição.
     */
    public SolicitacaoCadastro rejeitarSolicitacao(String cpf, String motivo) {
        if (motivo == null || motivo.trim().isBlank()) {
            throw new IllegalArgumentException("Motivo da rejeição é obrigatório.");
        }
        SolicitacaoCadastro solicitacao = buscarPorCpf(cpf);
        if (!StatusSolicitacao.PENDENTE.equalsIgnoreCase(solicitacao.getStatus())) {
            throw new IllegalStateException("Só é possível rejeitar uma solicitação pendente.");
        }

        solicitacao.setStatus(StatusSolicitacao.NAO_APROVADA);
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
