package com.bantads.ms_cliente.service;

import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import com.bantads.ms_cliente.repository.ClienteRepository;
import com.bantads.ms_cliente.repository.SolicitacaoCadastroRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoCadastroServiceTest {

    @Mock
    private SolicitacaoCadastroRepository solicitacaoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private AutoCadastroService service;

    private SolicitacaoCadastro solicitacaoValida;

    @BeforeEach
    void setUp() {
        solicitacaoValida = new SolicitacaoCadastro();
        solicitacaoValida.setNome("Maria Silva");
        solicitacaoValida.setCpf("12912861012");
        solicitacaoValida.setEmail("maria@bantads.com.br");
        solicitacaoValida.setTelefone("41999998888");
        solicitacaoValida.setSalario(new BigDecimal("3500.00"));
        solicitacaoValida.setLogradouro("Rua XV de Novembro");
        solicitacaoValida.setNumero("100");
        solicitacaoValida.setCep("80000000");
        solicitacaoValida.setCidade("Curitiba");
        solicitacaoValida.setUf("PR");
    }

    @Test
    @DisplayName("Deve registrar solicitação com sucesso quando campos são válidos e não há duplicidade")
    void deveRegistrarSolicitacaoComSucesso() {
        when(solicitacaoRepository.existsByCpf("12912861012")).thenReturn(false);
        when(clienteRepository.existsById("12912861012")).thenReturn(false);
        when(solicitacaoRepository.existsByEmail("maria@bantads.com.br")).thenReturn(false);
        when(clienteRepository.existsByEmail("maria@bantads.com.br")).thenReturn(false);
        when(solicitacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitacaoCadastro salva = service.registrarSolicitacao(solicitacaoValida);

        assertNotNull(salva);
        assertEquals("PENDENTE", salva.getStatus());
        verify(solicitacaoRepository, times(1)).save(solicitacaoValida);
    }

    @Test
    @DisplayName("Deve rejeitar solicitação quando CPF já existe em outra solicitação")
    void deveRejeitarQuandoCpfExisteEmSolicitacao() {
        when(solicitacaoRepository.existsByCpf("12912861012")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.registrarSolicitacao(solicitacaoValida)
        );

        assertTrue(ex.getMessage().contains("CPF já possui uma solicitação"));
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar solicitação quando CPF já pertence a um cliente já cadastrado (R1)")
    void deveRejeitarQuandoCpfJaPertenceAClienteCadastrado() {
        when(solicitacaoRepository.existsByCpf("12912861012")).thenReturn(false);
        when(clienteRepository.existsById("12912861012")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.registrarSolicitacao(solicitacaoValida)
        );

        assertTrue(ex.getMessage().contains("CPF já cadastrado como cliente"));
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar solicitação quando e-mail já pertence a um cliente já cadastrado (R1)")
    void deveRejeitarQuandoEmailJaPertenceAClienteCadastrado() {
        when(solicitacaoRepository.existsByCpf("12912861012")).thenReturn(false);
        when(clienteRepository.existsById("12912861012")).thenReturn(false);
        when(solicitacaoRepository.existsByEmail("maria@bantads.com.br")).thenReturn(false);
        when(clienteRepository.existsByEmail("maria@bantads.com.br")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.registrarSolicitacao(solicitacaoValida)
        );

        assertTrue(ex.getMessage().contains("E-mail já cadastrado como cliente"));
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar solicitação quando campo obrigatório for inválido")
    void deveRejeitarQuandoCamposInvalidos() {
        solicitacaoValida.setNome("");
        assertThrows(IllegalArgumentException.class, () -> service.registrarSolicitacao(solicitacaoValida));

        solicitacaoValida.setNome("Maria");
        solicitacaoValida.setCpf("123");
        assertThrows(IllegalArgumentException.class, () -> service.registrarSolicitacao(solicitacaoValida));

        solicitacaoValida.setCpf("12912861012");
        solicitacaoValida.setEmail("invalido");
        assertThrows(IllegalArgumentException.class, () -> service.registrarSolicitacao(solicitacaoValida));

        solicitacaoValida.setEmail("maria@bantads.com.br");
        solicitacaoValida.setSalario(new BigDecimal("-10"));
        assertThrows(IllegalArgumentException.class, () -> service.registrarSolicitacao(solicitacaoValida));
    }
}
