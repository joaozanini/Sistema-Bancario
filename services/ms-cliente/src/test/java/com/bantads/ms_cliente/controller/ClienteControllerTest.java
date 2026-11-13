package com.bantads.ms_cliente.controller;

import com.bantads.ms_cliente.model.Cliente;
import com.bantads.ms_cliente.service.ClienteService;
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
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClienteControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ClienteService service;

    @InjectMocks
    private ClienteController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("Deve buscar o cliente pelo CPF com o endereço aninhado e os _links HATEOAS")
    void deveBuscarClienteComLinks() throws Exception {
        Cliente cliente = new Cliente();
        cliente.setCpf("12912861012");
        cliente.setNome("Catharyna");
        cliente.setEmail("cli1@bantads.com.br");
        cliente.setSalario(new BigDecimal("10000"));
        cliente.setLogradouro("Rua XV de Novembro");
        cliente.setNumero("100");
        cliente.setCep("80020310");
        cliente.setCidade("Curitiba");
        cliente.setUf("PR");

        when(service.buscarPorCpf("12912861012")).thenReturn(Optional.of(cliente));

        mockMvc.perform(get("/clientes/12912861012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value("12912861012"))
                .andExpect(jsonPath("$.salario").value("10000.00"))
                .andExpect(jsonPath("$.endereco.logradouro").value("Rua XV de Novembro"))
                .andExpect(jsonPath("$.endereco.cep").value("80020310"))
                .andExpect(jsonPath("$.endereco.cidade").value("Curitiba"))
                .andExpect(jsonPath("$.endereco.uf").value("PR"))
                .andExpect(jsonPath("$.cidade").doesNotExist())
                .andExpect(jsonPath("$._links.self.href").value("http://localhost:8082/clientes/12912861012"))
                .andExpect(jsonPath("$._links.conta.href").value("http://localhost:8082/clientes/12912861012/conta"));
    }

    @Test
    @DisplayName("Deve responder 404 quando o cliente não existe")
    void deveResponder404QuandoClienteNaoExiste() throws Exception {
        when(service.buscarPorCpf("00000000000")).thenReturn(Optional.empty());

        mockMvc.perform(get("/clientes/00000000000"))
                .andExpect(status().isNotFound());
    }
}
