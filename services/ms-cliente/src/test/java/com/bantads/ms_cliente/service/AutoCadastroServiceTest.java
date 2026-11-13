package com.bantads.ms_cliente.service;

import com.bantads.ms_cliente.exception.CadastroDuplicadoException;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoCadastroServiceTest {

    @Mock
    private SolicitacaoCadastroRepository solicitacaoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

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
        when(clienteRepository.existsByCpf("12912861012")).thenReturn(false);
        when(solicitacaoRepository.existsByEmail("maria@bantads.com.br")).thenReturn(false);
        when(solicitacaoRepository.existsByEmailIgnoreCase("maria@bantads.com.br")).thenReturn(false);
        when(clienteRepository.existsByEmail("maria@bantads.com.br")).thenReturn(false);
        when(clienteRepository.existsByEmailIgnoreCase("maria@bantads.com.br")).thenReturn(false);
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

        // CadastroDuplicadoException vira 409 no controller (R1: CPF duplicado)
        CadastroDuplicadoException ex = assertThrows(CadastroDuplicadoException.class, () ->
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
        when(clienteRepository.existsByCpf("12912861012")).thenReturn(false);
        when(solicitacaoRepository.existsByEmail("maria@bantads.com.br")).thenReturn(false);
        when(solicitacaoRepository.existsByEmailIgnoreCase("maria@bantads.com.br")).thenReturn(false);
        when(clienteRepository.existsByEmail("maria@bantads.com.br")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.registrarSolicitacao(solicitacaoValida)
        );

        assertTrue(ex.getMessage().contains("E-mail já cadastrado como cliente"));
        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar solicitação quando e-mail difere apenas em maiúsculas/minúsculas (case-insensitive)")
    void deveRejeitarQuandoEmailDuplicadoCaseInsensitive() {
        solicitacaoValida.setEmail("MARIA@Bantads.com.br");
        when(solicitacaoRepository.existsByCpf("12912861012")).thenReturn(false);
        when(clienteRepository.existsById("12912861012")).thenReturn(false);
        when(clienteRepository.existsByCpf("12912861012")).thenReturn(false);
        when(solicitacaoRepository.existsByEmail("MARIA@Bantads.com.br")).thenReturn(false);
        when(solicitacaoRepository.existsByEmailIgnoreCase("MARIA@Bantads.com.br")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.registrarSolicitacao(solicitacaoValida)
        );

        assertTrue(ex.getMessage().contains("E-mail já utilizado em outra solicitação"));
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

    @Test
    @DisplayName("Deve gravar o nome sem espaços nas pontas, para não bagunçar a ordenação por nome (R11, R16)")
    void deveGravarNomeSemEspacosNasPontas() {
        solicitacaoValida.setNome("  Maria Silva ");
        when(solicitacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitacaoCadastro salva = service.registrarSolicitacao(solicitacaoValida);

        assertEquals("Maria Silva", salva.getNome());
    }

    @Test
    @DisplayName("Deve gravar o CEP só com dígitos e a UF em maiúsculas, como no contrato (R1)")
    void deveNormalizarCepEUf() {
        solicitacaoValida.setCep("80000-000");
        solicitacaoValida.setUf("pr");
        solicitacaoValida.setComplemento("   ");
        when(solicitacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitacaoCadastro salva = service.registrarSolicitacao(solicitacaoValida);

        assertEquals("80000000", salva.getCep());
        assertEquals("PR", salva.getUf());
        assertNull(salva.getComplemento());
    }

    @Test
    @DisplayName("Deve rejeitar solicitação com endereço fora do formato (R1)")
    void deveRejeitarEnderecoInvalido() {
        solicitacaoValida.setCep("8000");
        assertThrows(IllegalArgumentException.class, () -> service.registrarSolicitacao(solicitacaoValida));

        solicitacaoValida.setCep("80000000");
        solicitacaoValida.setUf("Parana");
        assertThrows(IllegalArgumentException.class, () -> service.registrarSolicitacao(solicitacaoValida));

        solicitacaoValida.setUf("PR");
        solicitacaoValida.setNumero("12345678901");
        assertThrows(IllegalArgumentException.class, () -> service.registrarSolicitacao(solicitacaoValida));

        verify(solicitacaoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve filtrar a listagem pelo status do contrato e aceitar a grafia antiga (R8)")
    void deveFiltrarListagemPorStatus() {
        when(solicitacaoRepository.findByStatusIgnoreCase("APROVADA")).thenReturn(List.of(solicitacaoValida));

        assertEquals(1, service.listarSolicitacoes("APROVADA").size());
        assertEquals(1, service.listarSolicitacoes("aprovado").size());
        assertThrows(IllegalArgumentException.class, () -> service.listarSolicitacoes("QUALQUER"));
    }

    @Test
    @DisplayName("Deve rejeitar pelo CPF uma solicitação pendente, gravando motivo e data/hora (R10)")
    void deveRejeitarSolicitacaoPendentePeloCpf() {
        solicitacaoValida.setStatus("PENDENTE");
        when(solicitacaoRepository.findByCpf("12912861012")).thenReturn(Optional.of(solicitacaoValida));
        when(solicitacaoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SolicitacaoCadastro rejeitada = service.rejeitarSolicitacao("12912861012", " renda insuficiente ");

        assertEquals("NAO_APROVADA", rejeitada.getStatus());
        assertEquals("renda insuficiente", rejeitada.getMotivoRejeicao());
        assertNotNull(rejeitada.getDataAprovacaoRejeicao());
        // o e-mail com o motivo sai por ms.email.cmd, sem esperar resposta
        verify(rabbitTemplate).convertAndSend(eq(AutoCadastroService.FILA_EMAIL_CMD), any(Object.class));
    }

    @Test
    @DisplayName("Rejeição de solicitação inexistente, sem motivo ou já processada não grava nada (R10)")
    void naoDeveRejeitarSolicitacaoInvalida() {
        when(solicitacaoRepository.findByCpf("00000000000")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.rejeitarSolicitacao("00000000000", "x"));

        assertThrows(IllegalArgumentException.class, () -> service.rejeitarSolicitacao("12912861012", "  "));

        solicitacaoValida.setStatus("APROVADA");
        when(solicitacaoRepository.findByCpf("12912861012")).thenReturn(Optional.of(solicitacaoValida));
        assertThrows(IllegalStateException.class, () -> service.rejeitarSolicitacao("12912861012", "x"));

        verify(solicitacaoRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }
}
