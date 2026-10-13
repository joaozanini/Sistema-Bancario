package com.bantads.ms_cliente.controller;

import com.bantads.ms_cliente.dto.SolicitacaoCadastroDTO;
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
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    @DisplayName("Deve listar todas as solicitações de autocadastro (R8)")
    void deveListarSolicitacoes() throws Exception {
        SolicitacaoCadastro s1 = new SolicitacaoCadastro();
        s1.setId(UUID.randomUUID());
        s1.setNome("Catharyna");
        s1.setCpf("12912861012");
        s1.setEmail("cli1@bantads.com.br");
        s1.setSalario(new BigDecimal("10000.00"));
        s1.setStatus("PENDENTE");

        SolicitacaoCadastro s2 = new SolicitacaoCadastro();
        s2.setId(UUID.randomUUID());
        s2.setNome("Cutardo");
        s2.setCpf("58872160006");
        s2.setEmail("cli4@bantads.com.br");
        s2.setSalario(new BigDecimal("500.00"));
        s2.setStatus("APROVADO");
        s2.setDataAprovacaoRejeicao(LocalDateTime.now());

        when(service.listarSolicitacoes(null)).thenReturn(List.of(s1, s2));

        mockMvc.perform(get("/solicitacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nome").value("Catharyna"))
                .andExpect(jsonPath("$[0].cpf").value("12912861012"))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$[0]._links.self.href").exists())
                .andExpect(jsonPath("$[1].nome").value("Cutardo"))
                .andExpect(jsonPath("$[1].status").value("APROVADO"));
    }

    @Test
    @DisplayName("Deve listar solicitações filtradas por status (R8)")
    void deveListarSolicitacoesPorStatus() throws Exception {
        SolicitacaoCadastro s1 = new SolicitacaoCadastro();
        s1.setId(UUID.randomUUID());
        s1.setNome("Catharyna");
        s1.setCpf("12912861012");
        s1.setEmail("cli1@bantads.com.br");
        s1.setSalario(new BigDecimal("10000.00"));
        s1.setStatus("PENDENTE");

        when(service.listarSolicitacoes("PENDENTE")).thenReturn(List.of(s1));

        mockMvc.perform(get("/solicitacoes").param("status", "PENDENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"));
    }
}
