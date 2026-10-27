package com.bantads.ms_conta.controller;

import com.bantads.ms_conta.dto.*;
import com.bantads.ms_conta.exception.AcessoNegadoException;
import com.bantads.ms_conta.exception.GlobalExceptionHandler;
import com.bantads.ms_conta.exception.SaldoInsuficienteException;
import com.bantads.ms_conta.service.ContaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ContaControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ContaService service;

    @InjectMocks
    private ContaController controller;

    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        jsonMapper = JsonMapper.builder().build();
    }

    @Test
    @DisplayName("Deve retornar 200 ao realizar transferência com sucesso")
    void deveTransferirComSucesso() throws Exception {
        TransferenciaRequestDTO request = new TransferenciaRequestDTO("0950", "250.00");
        TransferenciaResponseDTO response = new TransferenciaResponseDTO();
        response.setContaOrigem("1291");
        response.setContaDestino("0950");
        response.setValor("250.00");
        response.setDataHora(LocalDateTime.now());
        response.setMensagem("Transferência realizada com sucesso.");
        response.addLink("self", "http://localhost:8080/contas/1291/transferencia");

        when(service.transferir(eq("1291"), any(TransferenciaRequestDTO.class), eq("12912861012")))
                .thenReturn(response);

        mockMvc.perform(post("/contas/1291/transferencia")
                        .header("X-User-CPF", "12912861012")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contaOrigem").value("1291"))
                .andExpect(jsonPath("$.contaDestino").value("0950"))
                .andExpect(jsonPath("$.valor").value("250.00"))
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    @Test
    @DisplayName("Deve retornar 422 ao tentar transferir com saldo insuficiente (R6)")
    void deveRetornar422QuandoSaldoInsuficienteNaTransferencia() throws Exception {
        TransferenciaRequestDTO request = new TransferenciaRequestDTO("0950", "9999.00");

        when(service.transferir(eq("1291"), any(TransferenciaRequestDTO.class), eq("12912861012")))
                .thenThrow(new SaldoInsuficienteException("Saldo insuficiente para realizar a transferência."));

        mockMvc.perform(post("/contas/1291/transferencia")
                        .header("X-User-CPF", "12912861012")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Saldo insuficiente para realizar a transferência."));
    }

    @Test
    @DisplayName("Deve retornar 403 quando posse obrigatória não for atendida (R5, R6)")
    void deveRetornar403QuandoPosseInvalida() throws Exception {
        TransferenciaRequestDTO request = new TransferenciaRequestDTO("0950", "100.00");

        when(service.transferir(eq("1291"), any(TransferenciaRequestDTO.class), eq("99999999999")))
                .thenThrow(new AcessoNegadoException("Usuário não autorizado a realizar operações nesta conta."));

        mockMvc.perform(post("/contas/1291/transferencia")
                        .header("X-User-CPF", "99999999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("Usuário não autorizado a realizar operações nesta conta."));
    }

    @Test
    @DisplayName("Deve retornar 201 sem o novo saldo ao realizar saque (R5)")
    void deveSacarComSucesso() throws Exception {
        MovimentacaoRequestDTO request = new MovimentacaoRequestDTO("150.00");
        MovimentacaoResponseDTO response = new MovimentacaoResponseDTO();
        response.setNumeroConta("1291");
        response.setTipo("SAQUE");
        response.setValor("150.00");
        response.setDataHora(LocalDateTime.now());
        response.setMensagem("Saque realizado com sucesso.");
        response.addLink("self", "http://localhost:8080/contas/1291/saque");

        when(service.sacar(eq("1291"), any(MovimentacaoRequestDTO.class), eq("12912861012")))
                .thenReturn(response);

        mockMvc.perform(post("/contas/1291/saque")
                        .header("X-User-CPF", "12912861012")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroConta").value("1291"))
                .andExpect(jsonPath("$.tipo").value("SAQUE"))
                .andExpect(jsonPath("$.valor").value("150.00"))
                .andExpect(jsonPath("$.saldo").doesNotExist())
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    @Test
    @DisplayName("Deve retornar 201 sem o novo saldo ao realizar depósito (R4)")
    void deveDepositarComSucesso() throws Exception {
        MovimentacaoResponseDTO response = new MovimentacaoResponseDTO();
        response.setNumeroConta("1291");
        response.setTipo("DEPOSITO");
        response.setValor("150.00");
        response.setDataHora(LocalDateTime.now());
        response.setMensagem("Depósito realizado com sucesso.");
        response.addLink("self", "http://localhost:8080/contas/1291/deposito");

        when(service.depositar(eq("1291"), any(MovimentacaoRequestDTO.class), eq("12912861012")))
                .thenReturn(response);

        mockMvc.perform(post("/contas/1291/deposito")
                        .header("X-User-CPF", "12912861012")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new MovimentacaoRequestDTO("150.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DEPOSITO"))
                .andExpect(jsonPath("$.saldo").doesNotExist())
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    @Test
    @DisplayName("Deve retornar 403 ao depositar na conta de outro cliente (R4)")
    void deveRetornar403AoDepositarEmContaAlheia() throws Exception {
        when(service.depositar(eq("0950"), any(MovimentacaoRequestDTO.class), eq("12912861012")))
                .thenThrow(new AcessoNegadoException("Usuário não autorizado a realizar operações nesta conta."));

        mockMvc.perform(post("/contas/0950/deposito")
                        .header("X-User-CPF", "12912861012")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new MovimentacaoRequestDTO("10.00"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("Usuário não autorizado a realizar operações nesta conta."));
    }

    @Test
    @DisplayName("Deve retornar 422 ao tentar sacar com saldo insuficiente (R5)")
    void deveRetornar422QuandoSaldoInsuficienteNoSaque() throws Exception {
        MovimentacaoRequestDTO request = new MovimentacaoRequestDTO("5000.00");

        when(service.sacar(eq("1291"), any(MovimentacaoRequestDTO.class), eq("12912861012")))
                .thenThrow(new SaldoInsuficienteException("Saldo insuficiente para realizar o saque."));

        mockMvc.perform(post("/contas/1291/saque")
                        .header("X-User-CPF", "12912861012")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("Saldo insuficiente para realizar o saque."));
    }

    @Test
    @DisplayName("Deve buscar conta por número com sucesso")
    void deveBuscarContaPorNumero() throws Exception {
        ContaDTO dto = new ContaDTO("1291", "12912861012", "98574307084");
        dto.addLink("self", "http://localhost:8080/contas/1291");

        when(service.buscarContaPorNumero("1291")).thenReturn(dto);

        mockMvc.perform(get("/contas/1291"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroConta").value("1291"))
                .andExpect(jsonPath("$.cpfCliente").value("12912861012"))
                .andExpect(jsonPath("$.gerenteCpf").value("98574307084"))
                .andExpect(jsonPath("$._links.self.href").exists());
    }
}
