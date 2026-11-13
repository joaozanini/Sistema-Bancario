package com.bantads.ms_conta.controller;

import com.bantads.ms_conta.dto.ContaDTO;
import com.bantads.ms_conta.exception.AcessoNegadoException;
import com.bantads.ms_conta.exception.ContaNaoEncontradaException;
import com.bantads.ms_conta.exception.GlobalExceptionHandler;
import com.bantads.ms_conta.service.ContaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContaDoClienteControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContaService service;

    @InjectMocks
    private ContaDoClienteController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Deve devolver a conta do cliente com número, saldo e links das operações (R3)")
    void deveBuscarContaDoCliente() throws Exception {
        ContaDTO dto = new ContaDTO("1291", "12912861012", "98574307084");
        dto.setSaldo("800.00");
        dto.setDataCriacao("2000-01-01");
        dto.addLink("self", "http://localhost:8080/contas/1291");
        dto.addLink("deposito", "http://localhost:8080/contas/1291/deposito");

        when(service.buscarContaDoCliente("12912861012", "12912861012", "CLIENTE")).thenReturn(dto);

        mockMvc.perform(get("/clientes/12912861012/conta")
                        .header("X-User-CPF", "12912861012")
                        .header("X-User-Tipo", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("1291"))
                .andExpect(jsonPath("$.saldo").value("800.00"))
                .andExpect(jsonPath("$._links.deposito.href").value("http://localhost:8080/contas/1291/deposito"));
    }

    @Test
    @DisplayName("Deve responder 403 para a conta de outro cliente e 404 para cliente sem conta (R3)")
    void deveRecusarContaAlheiaOuInexistente() throws Exception {
        when(service.buscarContaDoCliente("09506382000", "12912861012", "CLIENTE"))
                .thenThrow(new AcessoNegadoException("Usuário não autorizado a consultar esta conta."));
        when(service.buscarContaDoCliente("00000000000", "98574307084", "GERENTE"))
                .thenThrow(new ContaNaoEncontradaException("Conta não encontrada para o cliente: 00000000000"));

        mockMvc.perform(get("/clientes/09506382000/conta")
                        .header("X-User-CPF", "12912861012")
                        .header("X-User-Tipo", "CLIENTE"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/clientes/00000000000/conta")
                        .header("X-User-CPF", "98574307084")
                        .header("X-User-Tipo", "GERENTE"))
                .andExpect(status().isNotFound());
    }
}
