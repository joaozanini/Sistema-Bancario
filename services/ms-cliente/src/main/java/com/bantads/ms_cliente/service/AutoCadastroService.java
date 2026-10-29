package com.bantads.ms_cliente.service;

import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import com.bantads.ms_cliente.repository.ClienteRepository;
import com.bantads.ms_cliente.repository.SolicitacaoCadastroRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AutoCadastroService {

    private final SolicitacaoCadastroRepository solicitacaoRepository;
    private final ClienteRepository clienteRepository;

    public AutoCadastroService(
            SolicitacaoCadastroRepository solicitacaoRepository,
            ClienteRepository clienteRepository
    ) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.clienteRepository = clienteRepository;
    }

    public SolicitacaoCadastro registrarSolicitacao(SolicitacaoCadastro solicitacao) {
        validarCampos(solicitacao);

        String cpf = solicitacao.getCpf();
        String email = solicitacao.getEmail();

        // Unicidade de CPF contra solicitações e contra clientes já cadastrados (R1)
        if (solicitacaoRepository.existsByCpf(cpf)) {
            throw new IllegalArgumentException("CPF já possui uma solicitação de cadastro.");
        }
        if (clienteRepository.existsById(cpf) || clienteRepository.existsByCpf(cpf)) {
            throw new IllegalArgumentException("CPF já cadastrado como cliente no sistema.");
        }

        // Unicidade de E-mail contra solicitações e contra clientes já cadastrados (R1)
        if (solicitacaoRepository.existsByEmail(email) || solicitacaoRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("E-mail já utilizado em outra solicitação.");
        }
        if (clienteRepository.existsByEmail(email) || clienteRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("E-mail já cadastrado como cliente no sistema.");
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
}
