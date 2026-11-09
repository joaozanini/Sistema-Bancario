package com.bantads.ms_cliente.service;

import com.bantads.ms_cliente.model.Cliente;
import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import com.bantads.ms_cliente.repository.ClienteRepository;
import com.bantads.ms_cliente.repository.SolicitacaoCadastroRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private SolicitacaoCadastroRepository solicitacaoRepository;

    @InjectMocks
    private ClienteService service;

    private Cliente criarCliente(String cpf, String nome, String email, String salario) {
        Cliente c = new Cliente();
        c.setCpf(cpf);
        c.setNome(nome);
        c.setEmail(email);
        c.setTelefone("41988880001");
        c.setSalario(new BigDecimal(salario));
        c.setLogradouro("Rua XV de Novembro");
        c.setNumero("100");
        c.setCep("80020310");
        c.setCidade("Curitiba");
        c.setUf("PR");
        return c;
    }

    @Test
    @DisplayName("Deve obter dados dos clientes transferidos com sucesso na SAGA Remover Gerente (R15)")
    void deveObterDadosClientesTransferidos() {
        Cliente c1 = criarCliente("12912861012", "Catharyna", "cli1@bantads.com.br", "10000.00");
        Cliente c2 = criarCliente("09506382000", "Cleuddônio", "cli2@bantads.com.br", "20000.00");

        when(clienteRepository.findById("12912861012")).thenReturn(Optional.of(c1));
        when(clienteRepository.findById("09506382000")).thenReturn(Optional.of(c2));

        List<Map<String, Object>> resultado = service.obterDadosClientesTransferidos(
                List.of("12912861012", "09506382000")
        );

        assertEquals(2, resultado.size());
        assertEquals("12912861012", resultado.get(0).get("cpf"));
        assertEquals("Catharyna", resultado.get(0).get("nome"));
        assertEquals("cli1@bantads.com.br", resultado.get(0).get("email"));
        assertEquals("10000.00", resultado.get(0).get("salario"));

        assertEquals("09506382000", resultado.get(1).get("cpf"));
        assertEquals("Cleuddônio", resultado.get(1).get("nome"));
        assertEquals("cli2@bantads.com.br", resultado.get(1).get("email"));
    }

    @Test
    @DisplayName("Deve retornar lista vazia quando nenhum cliente foi transferido (R15)")
    void deveRetornarListaVaziaQuandoSemClientesTransferidos() {
        List<Map<String, Object>> resultado = service.obterDadosClientesTransferidos(Collections.emptyList());

        assertTrue(resultado.isEmpty());
        verifyNoInteractions(clienteRepository);
    }

    @Test
    @DisplayName("Deve rejeitar criação de cliente quando o e-mail já está cadastrado em outro cliente")
    void deveRejeitarCriacaoDeClienteQuandoEmailJaCadastrado() {
        SolicitacaoCadastro solicitacao = new SolicitacaoCadastro();
        solicitacao.setCpf("11122233344");
        solicitacao.setNome("Novo Cliente");
        solicitacao.setEmail("cli1@bantads.com.br");
        solicitacao.setSalario(new BigDecimal("5000.00"));

        when(clienteRepository.findById("11122233344")).thenReturn(Optional.empty());
        when(solicitacaoRepository.findByCpf("11122233344")).thenReturn(Optional.of(solicitacao));
        when(clienteRepository.existsByEmail("cli1@bantads.com.br")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.criarClienteDaSolicitacao("11122233344")
        );

        assertTrue(ex.getMessage().contains("E-mail já cadastrado como cliente"));
        verify(clienteRepository, never()).save(any());
    }

    private SolicitacaoCadastro criarSolicitacao(String cpf, String status) {
        SolicitacaoCadastro solicitacao = new SolicitacaoCadastro();
        solicitacao.setCpf(cpf);
        solicitacao.setNome("Novo Cliente");
        solicitacao.setEmail("novo@bantads.com.br");
        solicitacao.setSalario(new BigDecimal("5000.00"));
        solicitacao.setStatus(status);
        return solicitacao;
    }

    @Test
    @DisplayName("Deve falhar ao aprovar uma solicitação que não está mais pendente (R9)")
    void deveFalharAoAprovarSolicitacaoNaoPendente() {
        SolicitacaoCadastro solicitacao = criarSolicitacao("11122233344", "APROVADO");
        when(solicitacaoRepository.findByCpf("11122233344")).thenReturn(Optional.of(solicitacao));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.aprovarSolicitacao("11122233344")
        );

        assertTrue(ex.getMessage().contains("não está pendente"));
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve aprovar uma solicitação pendente (R9)")
    void deveAprovarSolicitacaoPendente() {
        SolicitacaoCadastro solicitacao = criarSolicitacao("11122233344", "PENDENTE");
        when(solicitacaoRepository.findByCpf("11122233344")).thenReturn(Optional.of(solicitacao));

        Map<String, Object> dados = service.aprovarSolicitacao("11122233344");

        assertEquals("APROVADO", dados.get("status"));
        assertNotNull(solicitacao.getDataAprovacaoRejeicao());
        verify(solicitacaoRepository).save(solicitacao);
    }

    @Test
    @DisplayName("Compensação do passo 1 devolve para PENDENTE a aprovação desfeita pela SAGA (R9)")
    void deveCompensarAprovacaoQuandoClienteJaFoiRemovido() {
        SolicitacaoCadastro solicitacao = criarSolicitacao("11122233344", "APROVADO");
        when(solicitacaoRepository.findByCpf("11122233344")).thenReturn(Optional.of(solicitacao));
        when(clienteRepository.existsById("11122233344")).thenReturn(false);

        service.compensarAprovacaoSolicitacao("11122233344", "PENDENTE", null);

        assertEquals("PENDENTE", solicitacao.getStatus());
        assertNull(solicitacao.getDataAprovacaoRejeicao());
        verify(solicitacaoRepository).save(solicitacao);
    }

    @Test
    @DisplayName("Compensação do passo 1 não mexe na solicitação de um cliente que já existe (R9)")
    void naoDeveCompensarAprovacaoDeClienteExistente() {
        SolicitacaoCadastro solicitacao = criarSolicitacao("11122233344", "APROVADO");
        when(solicitacaoRepository.findByCpf("11122233344")).thenReturn(Optional.of(solicitacao));
        when(clienteRepository.existsById("11122233344")).thenReturn(true);

        service.compensarAprovacaoSolicitacao("11122233344", "PENDENTE", null);

        assertEquals("APROVADO", solicitacao.getStatus());
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Compensação do passo 1 ignora solicitação que não está APROVADO (R9)")
    void naoDeveCompensarSolicitacaoNaoAprovada() {
        SolicitacaoCadastro solicitacao = criarSolicitacao("11122233344", "NAO_APROVADA");
        when(solicitacaoRepository.findByCpf("11122233344")).thenReturn(Optional.of(solicitacao));

        service.compensarAprovacaoSolicitacao("11122233344", "PENDENTE", null);

        assertEquals("NAO_APROVADA", solicitacao.getStatus());
        verify(solicitacaoRepository, never()).save(any());
    }
}
