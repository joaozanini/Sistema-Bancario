package com.bantads.ms_cliente.service;

import com.bantads.ms_cliente.model.Cliente;
import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import com.bantads.ms_cliente.repository.ClienteRepository;
import com.bantads.ms_cliente.repository.SolicitacaoCadastroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ClienteService {

    private static final Logger log = LoggerFactory.getLogger(ClienteService.class);

    private final ClienteRepository clienteRepository;
    private final SolicitacaoCadastroRepository solicitacaoRepository;

    public ClienteService(
            ClienteRepository clienteRepository,
            SolicitacaoCadastroRepository solicitacaoRepository
    ) {
        this.clienteRepository = clienteRepository;
        this.solicitacaoRepository = solicitacaoRepository;
    }

    /**
     * SAGA Remover Gerente (R15): Obtém os dados cadastrais dos clientes cujas contas
     * foram transferidas do gerente removido para os gerentes remanescentes.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> obterDadosClientesTransferidos(List<String> cpfsClientes) {
        if (cpfsClientes == null || cpfsClientes.isEmpty()) {
            log.info("Nenhum CPF de cliente transferido informado na SAGA R15.");
            return Collections.emptyList();
        }

        List<Map<String, Object>> resultado = new ArrayList<>();
        Set<String> processados = new LinkedHashSet<>();

        for (String cpfBruto : cpfsClientes) {
            if (cpfBruto == null || cpfBruto.isBlank()) {
                continue;
            }
            String cpf = cpfBruto.replaceAll("\\D", "");
            if (cpf.isEmpty()) {
                cpf = cpfBruto.trim();
            }
            if (!processados.add(cpf)) {
                continue;
            }

            Optional<Cliente> optCliente = clienteRepository.findById(cpf);
            if (optCliente.isEmpty() && !cpf.equals(cpfBruto.trim())) {
                optCliente = clienteRepository.findById(cpfBruto.trim());
            }

            if (optCliente.isPresent()) {
                resultado.add(converterClienteParaMapa(optCliente.get()));
            } else {
                log.warn("Cliente transferido com CPF {} não encontrado na base do MS Cliente.", cpf);
            }
        }

        log.info("Dados obtidos para {} cliente(s) transferido(s) na SAGA Remover Gerente (R15).", resultado.size());
        return resultado;
    }

    /**
     * Obtém os dados cadastrais de um único cliente por CPF (usado em consultas de SAGA como R13/R15).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obterDadosClientePorCpf(String cpfBruto) {
        if (cpfBruto == null || cpfBruto.isBlank()) {
            throw new IllegalArgumentException("CPF do cliente é obrigatório.");
        }
        String cpf = cpfBruto.replaceAll("\\D", "");
        if (cpf.isEmpty()) {
            cpf = cpfBruto.trim();
        }
        Cliente cliente = clienteRepository.findById(cpf)
                .or(() -> clienteRepository.findById(cpfBruto.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado para o CPF: " + cpfBruto));

        return converterClienteParaMapa(cliente);
    }

    /**
     * SAGA Aprovar Cliente (R9 - Passo 1): Marca solicitação como APROVADO.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> aprovarSolicitacao(String cpfBruto) {
        if (cpfBruto == null || cpfBruto.isBlank()) {
            throw new IllegalArgumentException("CPF da solicitação é obrigatório.");
        }
        String cpf = cpfBruto.replaceAll("\\D", "");
        SolicitacaoCadastro solicitacao = solicitacaoRepository.findByCpf(cpf)
                .or(() -> solicitacaoRepository.findByCpf(cpfBruto.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Solicitação de cadastro não encontrada para o CPF: " + cpfBruto));

        solicitacao.setStatus("APROVADO");
        solicitacao.setDataAprovacaoRejeicao(LocalDateTime.now());
        solicitacaoRepository.save(solicitacao);

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("cpf", solicitacao.getCpf());
        dados.put("nome", solicitacao.getNome());
        dados.put("email", solicitacao.getEmail());
        dados.put("telefone", solicitacao.getTelefone());
        dados.put("salario", solicitacao.getSalario() != null
                ? solicitacao.getSalario().setScale(2, RoundingMode.HALF_UP).toPlainString()
                : "0.00");
        dados.put("status", solicitacao.getStatus());
        return dados;
    }

    /**
     * SAGA Aprovar Cliente (R9 - Compensação Passo 1): Reverte status da solicitação.
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensarAprovacaoSolicitacao(String cpfBruto, String novoStatus, String motivo) {
        if (cpfBruto == null || cpfBruto.isBlank()) {
            return;
        }
        String cpf = cpfBruto.replaceAll("\\D", "");
        solicitacaoRepository.findByCpf(cpf)
                .or(() -> solicitacaoRepository.findByCpf(cpfBruto.trim()))
                .ifPresent(solicitacao -> {
                    solicitacao.setStatus(novoStatus != null && !novoStatus.isBlank() ? novoStatus : "PENDENTE");
                    solicitacao.setMotivoRejeicao(motivo);
                    if ("PENDENTE".equalsIgnoreCase(solicitacao.getStatus())) {
                        solicitacao.setDataAprovacaoRejeicao(null);
                    } else {
                        solicitacao.setDataAprovacaoRejeicao(LocalDateTime.now());
                    }
                    solicitacaoRepository.save(solicitacao);
                });
    }

    /**
     * SAGA Aprovar Cliente (R9 - Passo 4): Cria o registro definitivo de Cliente a partir da solicitação.
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> criarClienteDaSolicitacao(String cpfBruto) {
        if (cpfBruto == null || cpfBruto.isBlank()) {
            throw new IllegalArgumentException("CPF do cliente é obrigatório.");
        }
        String cpf = cpfBruto.replaceAll("\\D", "");
        Optional<Cliente> existente = clienteRepository.findById(cpf);
        if (existente.isPresent()) {
            return converterClienteParaMapa(existente.get());
        }

        SolicitacaoCadastro solicitacao = solicitacaoRepository.findByCpf(cpf)
                .or(() -> solicitacaoRepository.findByCpf(cpfBruto.trim()))
                .orElseThrow(() -> new IllegalArgumentException("Solicitação não encontrada para criação do cliente: " + cpfBruto));

        Cliente cliente = new Cliente();
        cliente.setCpf(solicitacao.getCpf());
        cliente.setNome(solicitacao.getNome());
        cliente.setEmail(solicitacao.getEmail());
        cliente.setTelefone(solicitacao.getTelefone());
        cliente.setSalario(solicitacao.getSalario());
        cliente.setLogradouro(solicitacao.getLogradouro());
        cliente.setNumero(solicitacao.getNumero());
        cliente.setComplemento(solicitacao.getComplemento());
        cliente.setCep(solicitacao.getCep());
        cliente.setCidade(solicitacao.getCidade());
        cliente.setUf(solicitacao.getUf());

        Cliente salvo = clienteRepository.save(cliente);
        return converterClienteParaMapa(salvo);
    }

    /**
     * SAGA Aprovar Cliente (R9 - Compensação Passo 4): Remove cliente recém-criado.
     */
    @Transactional(rollbackFor = Exception.class)
    public void compensarCriacaoCliente(String cpfBruto) {
        if (cpfBruto == null || cpfBruto.isBlank()) {
            return;
        }
        String cpf = cpfBruto.replaceAll("\\D", "");
        if (clienteRepository.existsById(cpf)) {
            clienteRepository.deleteById(cpf);
        } else if (clienteRepository.existsById(cpfBruto.trim())) {
            clienteRepository.deleteById(cpfBruto.trim());
        }
    }

    private Map<String, Object> converterClienteParaMapa(Cliente cliente) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("cpf", cliente.getCpf());
        mapa.put("cpfCliente", cliente.getCpf());
        mapa.put("nome", cliente.getNome());
        mapa.put("email", cliente.getEmail());
        mapa.put("telefone", cliente.getTelefone());
        mapa.put("salario", cliente.getSalario() != null
                ? cliente.getSalario().setScale(2, RoundingMode.HALF_UP).toPlainString()
                : "0.00");
        mapa.put("logradouro", cliente.getLogradouro());
        mapa.put("numero", cliente.getNumero());
        mapa.put("complemento", cliente.getComplemento());
        mapa.put("cep", cliente.getCep());
        mapa.put("cidade", cliente.getCidade());
        mapa.put("uf", cliente.getUf());
        return mapa;
    }
}
