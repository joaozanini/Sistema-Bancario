package com.bantads.ms_cliente.controller;

import com.bantads.ms_cliente.dto.SolicitacaoCadastroDTO;
import com.bantads.ms_cliente.exception.CadastroDuplicadoException;
import com.bantads.ms_cliente.model.SolicitacaoCadastro;
import com.bantads.ms_cliente.service.AutoCadastroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AutoCadastroControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AutoCadastroService service;

    @InjectMocks
    private AutoCadastroController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private SolicitacaoCadastro solicitacao(String nome, String cpf, String email, String salario, String status) {
        SolicitacaoCadastro s = new SolicitacaoCadastro();
        s.setNome(nome);
        s.setCpf(cpf);
        s.setEmail(email);
        s.setTelefone("41999998888");
        s.setSalario(new BigDecimal(salario));
        s.setLogradouro("Rua XV de Novembro");
        s.setNumero("100");
        s.setCep("80000000");
        s.setCidade("Curitiba");
        s.setUf("PR");
        s.setStatus(status);
        return s;
    }

    @Test
    @DisplayName("Deve listar todas as solicitações de autocadastro dentro de { solicitacoes } (R8)")
    void deveListarSolicitacoes() throws Exception {
        SolicitacaoCadastro s1 = solicitacao("Catharyna", "12912861012", "cli1@bantads.com.br", "10000.0000", "PENDENTE");
        SolicitacaoCadastro s2 = solicitacao("Cutardo", "58872160006", "cli4@bantads.com.br", "500.00", "APROVADA");
        s2.setDataAprovacaoRejeicao(LocalDateTime.of(2026, 4, 30, 10, 0, 0));

        when(service.listarSolicitacoes(null)).thenReturn(List.of(s1, s2));

        mockMvc.perform(get("/solicitacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").value("http://localhost:8082/solicitacoes"))
                .andExpect(jsonPath("$.solicitacoes.length()").value(2))
                .andExpect(jsonPath("$.solicitacoes[0].nome").value("Catharyna"))
                .andExpect(jsonPath("$.solicitacoes[0].cpf").value("12912861012"))
                .andExpect(jsonPath("$.solicitacoes[0].salario").value("10000.00"))
                .andExpect(jsonPath("$.solicitacoes[0].endereco.cidade").value("Curitiba"))
                .andExpect(jsonPath("$.solicitacoes[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$.solicitacoes[0].dataHoraProcessamento").doesNotExist())
                .andExpect(jsonPath("$.solicitacoes[0]._links.self.href").value("http://localhost:8082/solicitacoes/12912861012"))
                .andExpect(jsonPath("$.solicitacoes[0]._links.aprovacao.href").value("http://localhost:8082/solicitacoes/12912861012/aprovacao"))
                .andExpect(jsonPath("$.solicitacoes[0]._links.rejeicao.href").value("http://localhost:8082/solicitacoes/12912861012/rejeicao"))
                .andExpect(jsonPath("$.solicitacoes[0]._links.cliente").doesNotExist())
                .andExpect(jsonPath("$.solicitacoes[1].nome").value("Cutardo"))
                .andExpect(jsonPath("$.solicitacoes[1].status").value("APROVADA"))
                .andExpect(jsonPath("$.solicitacoes[1].dataHoraProcessamento").value("2026-04-30T10:00:00"))
                .andExpect(jsonPath("$.solicitacoes[1]._links.cliente.href").value("http://localhost:8082/clientes/58872160006"))
                .andExpect(jsonPath("$.solicitacoes[1]._links.aprovacao").doesNotExist())
                .andExpect(jsonPath("$.solicitacoes[1]._links.rejeicao").doesNotExist());
    }

    @Test
    @DisplayName("Deve listar solicitações filtradas por status (R8)")
    void deveListarSolicitacoesPorStatus() throws Exception {
        SolicitacaoCadastro s1 = solicitacao("Catharyna", "12912861012", "cli1@bantads.com.br", "10000.00", "PENDENTE");

        when(service.listarSolicitacoes("PENDENTE")).thenReturn(List.of(s1));

        mockMvc.perform(get("/solicitacoes").param("status", "PENDENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solicitacoes.length()").value(1))
                .andExpect(jsonPath("$.solicitacoes[0].status").value("PENDENTE"));
    }

    @Test
    @DisplayName("Deve responder 400 quando o filtro de status não existe (R8)")
    void deveRetornar400QuandoStatusInvalido() throws Exception {
        when(service.listarSolicitacoes("QUALQUER")).thenThrow(new IllegalArgumentException("Status inválido."));

        mockMvc.perform(get("/solicitacoes").param("status", "QUALQUER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve consultar uma solicitação pelo CPF e responder 404 quando ela não existe (R8)")
    void deveConsultarSolicitacaoPorCpf() throws Exception {
        when(service.buscarPorCpf("12912861012"))
                .thenReturn(solicitacao("Catharyna", "12912861012", "cli1@bantads.com.br", "10000.00", "PENDENTE"));
        when(service.buscarPorCpf("00000000000")).thenThrow(new NoSuchElementException("Solicitação não encontrada."));

        mockMvc.perform(get("/solicitacoes/12912861012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value("12912861012"))
                .andExpect(jsonPath("$._links.self.href").value("http://localhost:8082/solicitacoes/12912861012"));

        mockMvc.perform(get("/solicitacoes/00000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Solicitação não encontrada."));
    }

    private static final String CORPO_SOLICITACAO = """
            {"nome": "Maria Silva", "cpf": "12912861012", "email": "maria@bantads.com.br",
             "telefone": "41999998888", "salario": "3500.00",
             "endereco": {"logradouro": "Rua XV de Novembro", "numero": "100", "complemento": null,
                          "cep": "80000000", "cidade": "Curitiba", "uf": "PR"}}
            """;

    @Test
    @DisplayName("Deve responder 201 com a solicitação PENDENTE, o Location e os links de aprovação e rejeição (R1)")
    void deveCriarSolicitacao() throws Exception {
        when(service.registrarSolicitacao(argThat(s ->
                "Rua XV de Novembro".equals(s.getLogradouro()) && "80000000".equals(s.getCep())
                        && "Curitiba".equals(s.getCidade()) && "PR".equals(s.getUf())
                        && new BigDecimal("3500.00").compareTo(s.getSalario()) == 0)))
                .thenAnswer(invocation -> {
                    SolicitacaoCadastro salva = invocation.getArgument(0);
                    salva.setStatus("PENDENTE");
                    return salva;
                });

        mockMvc.perform(post("/solicitacoes").contentType("application/json").content(CORPO_SOLICITACAO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/solicitacoes/12912861012"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.salario").value("3500.00"))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua XV de Novembro"))
                .andExpect(jsonPath("$.motivo").doesNotExist())
                .andExpect(jsonPath("$._links.aprovacao.href").value("http://localhost:8082/solicitacoes/12912861012/aprovacao"))
                .andExpect(jsonPath("$._links.rejeicao.href").value("http://localhost:8082/solicitacoes/12912861012/rejeicao"));
    }

    @Test
    @DisplayName("Deve responder 409 quando o CPF ou o e-mail já foi usado (R1)")
    void deveRetornar409QuandoCadastroDuplicado() throws Exception {
        when(service.registrarSolicitacao(any()))
                .thenThrow(new CadastroDuplicadoException("CPF já possui uma solicitação de cadastro."));

        mockMvc.perform(post("/solicitacoes").contentType("application/json").content(CORPO_SOLICITACAO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("CPF já possui uma solicitação de cadastro."));
    }

    @Test
    @DisplayName("Deve responder 400 quando o dado é inválido (R1)")
    void deveRetornar400QuandoDadoInvalido() throws Exception {
        when(service.registrarSolicitacao(any()))
                .thenThrow(new IllegalArgumentException("CPF deve conter 11 dígitos numéricos."));

        mockMvc.perform(post("/solicitacoes").contentType("application/json").content(CORPO_SOLICITACAO))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve responder 400 quando o salário não é um valor decimal simples (R1)")
    void deveRetornar400QuandoSalarioInvalido() throws Exception {
        String corpo = CORPO_SOLICITACAO.replace("\"3500.00\"", "\"1E-99999999\"");

        mockMvc.perform(post("/solicitacoes").contentType("application/json").content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Salário inválido."));
    }

    @Test
    @DisplayName("Deve rejeitar pelo CPF e responder 200 com o status NAO_APROVADA e o motivo (R10)")
    void deveRejeitarSolicitacao() throws Exception {
        SolicitacaoCadastro rejeitada = solicitacao("Beltrano", "11122233344", "beltrano@bantads.com.br", "800.00", "NAO_APROVADA");
        rejeitada.setMotivoRejeicao("renda insuficiente");
        rejeitada.setDataAprovacaoRejeicao(LocalDateTime.of(2026, 4, 30, 10, 0, 0));
        when(service.rejeitarSolicitacao("11122233344", "renda insuficiente")).thenReturn(rejeitada);

        mockMvc.perform(post("/solicitacoes/11122233344/rejeicao")
                        .contentType("application/json").content("{\"motivo\": \"renda insuficiente\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NAO_APROVADA"))
                .andExpect(jsonPath("$.motivo").value("renda insuficiente"))
                .andExpect(jsonPath("$.dataHoraProcessamento").value("2026-04-30T10:00:00"))
                .andExpect(jsonPath("$._links.aprovacao").doesNotExist());
    }

    @Test
    @DisplayName("Rejeição responde 404 para solicitação inexistente e 409 para a que não está pendente (R10)")
    void deveRecusarRejeicaoInvalida() throws Exception {
        when(service.rejeitarSolicitacao("00000000000", "x"))
                .thenThrow(new NoSuchElementException("Solicitação não encontrada."));
        when(service.rejeitarSolicitacao("12912861012", "x"))
                .thenThrow(new IllegalStateException("Só é possível rejeitar uma solicitação pendente."));

        mockMvc.perform(post("/solicitacoes/00000000000/rejeicao")
                        .contentType("application/json").content("{\"motivo\": \"x\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/solicitacoes/12912861012/rejeicao")
                        .contentType("application/json").content("{\"motivo\": \"x\"}"))
                .andExpect(status().isConflict());
    }
}
