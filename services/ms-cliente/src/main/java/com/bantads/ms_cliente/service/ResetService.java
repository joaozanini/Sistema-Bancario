package com.bantads.ms_cliente.service;

import com.bantads.ms_cliente.model.Cliente;
import com.bantads.ms_cliente.repository.ClienteRepository;
import com.bantads.ms_cliente.repository.SagaIdempotenciaRepository;
import com.bantads.ms_cliente.repository.SolicitacaoCadastroRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ResetService {

    private final ClienteRepository clienteRepository;
    private final SolicitacaoCadastroRepository solicitacaoRepository;
    private final SagaIdempotenciaRepository idempotenciaRepository;

    public ResetService(
            ClienteRepository clienteRepository,
            SolicitacaoCadastroRepository solicitacaoRepository,
            SagaIdempotenciaRepository idempotenciaRepository
    ) {
        this.clienteRepository = clienteRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.idempotenciaRepository = idempotenciaRepository;
    }

    // Clientes pre-cadastrados (enunciado, secao 4); solicitacoes ficam vazias (5.11)
    @Transactional
    public void reset() {
        idempotenciaRepository.deleteAllInBatch();
        solicitacaoRepository.deleteAllInBatch();
        clienteRepository.deleteAllInBatch();
        clienteRepository.saveAll(List.of(
                cliente("12912861012", "Catharyna", "cli1@bantads.com.br", "41988880001", "10000.00", "Rua XV de Novembro", "100", null, "80020310"),
                cliente("09506382000", "Cleuddônio", "cli2@bantads.com.br", "41988880002", "20000.00", "Avenida Sete de Setembro", "200", "Apto 12", "80230010"),
                cliente("85733854057", "Catianna", "cli3@bantads.com.br", "41988880003", "3000.00", "Rua Marechal Deodoro", "300", null, "80010010"),
                cliente("58872160006", "Cutardo", "cli4@bantads.com.br", "41988880004", "500.00", "Rua Comendador Araújo", "400", "Casa", "80420000"),
                cliente("76179646090", "Coândrya", "cli5@bantads.com.br", "41988880005", "1500.00", "Avenida Visconde de Guarapuava", "500", null, "80060060")
        ));
    }

    private Cliente cliente(String cpf, String nome, String email, String telefone, String salario,
                            String logradouro, String numero, String complemento, String cep) {
        Cliente cliente = new Cliente();
        cliente.setCpf(cpf);
        cliente.setNome(nome);
        cliente.setEmail(email);
        cliente.setTelefone(telefone);
        cliente.setSalario(new BigDecimal(salario));
        cliente.setLogradouro(logradouro);
        cliente.setNumero(numero);
        cliente.setComplemento(complemento);
        cliente.setCep(cep);
        cliente.setCidade("Curitiba");
        cliente.setUf("PR");
        return cliente;
    }
}
