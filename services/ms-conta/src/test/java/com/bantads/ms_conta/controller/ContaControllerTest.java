package com.bantads.ms_conta.controller;

import com.bantads.ms_conta.dto.*;
import com.bantads.ms_conta.exception.AcessoNegadoException;
import com.bantads.ms_conta.exception.GlobalExceptionHandler;
import com.bantads.ms_conta.exception.RegraNegocioException;
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
import java.util.List;

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
    @DisplayName("Deve retornar 201 com o destino e sem o novo saldo ao realizar transferência com sucesso (R6)")
    void deveTransferirComSucesso() throws Exception {
        TransferenciaRequestDTO request = new TransferenciaRequestDTO("0950", "250.00");
        MovimentacaoResponseDTO response = new MovimentacaoResponseDTO();
        response.setNumeroConta("1291");
        response.setTipo("TRANSFERENCIA");
        response.setValor("250.00");
        response.setDataHora(LocalDateTime.of(2026, 4, 30, 10, 0, 0, 123_000_000));
        response.setDestino(new ParteTransferenciaDTO("0950", "09506382000", "Cleuddônio"));
        response.addLink("conta", "http://localhost:8080/contas/1291");

        when(service.transferir(eq("1291"), any(TransferenciaRequestDTO.class), eq("12912861012")))
                .thenReturn(response);

        mockMvc.perform(post("/contas/1291/transferencia")
                        .header("X-User-CPF", "12912861012")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroConta").value("1291"))
                .andExpect(jsonPath("$.tipo").value("TRANSFERENCIA"))
                .andExpect(jsonPath("$.destino.numeroConta").value("0950"))
                .andExpect(jsonPath("$.destino.cpf").value("09506382000"))
                .andExpect(jsonPath("$.destino.nome").value("Cleuddônio"))
                .andExpect(jsonPath("$.valor").value("250.00"))
                // data/hora sem fração de segundo, como no contrato
                .andExpect(jsonPath("$.dataHora").value("2026-04-30T10:00:00"))
                .andExpect(jsonPath("$.saldo").doesNotExist())
                .andExpect(jsonPath("$._links.conta.href").exists());
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
        response.addLink("conta", "http://localhost:8080/contas/1291");

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
                // "destino" só existe na transferência
                .andExpect(jsonPath("$.destino").doesNotExist())
                .andExpect(jsonPath("$._links.conta.href").exists());
    }

    @Test
    @DisplayName("Deve retornar 201 sem o novo saldo ao realizar depósito (R4)")
    void deveDepositarComSucesso() throws Exception {
        MovimentacaoResponseDTO response = new MovimentacaoResponseDTO();
        response.setNumeroConta("1291");
        response.setTipo("DEPOSITO");
        response.setValor("150.00");
        response.setDataHora(LocalDateTime.now());
        response.addLink("conta", "http://localhost:8080/contas/1291");

        when(service.depositar(eq("1291"), any(MovimentacaoRequestDTO.class), eq("12912861012")))
                .thenReturn(response);

        mockMvc.perform(post("/contas/1291/deposito")
                        .header("X-User-CPF", "12912861012")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(new MovimentacaoRequestDTO("150.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DEPOSITO"))
                .andExpect(jsonPath("$.saldo").doesNotExist())
                .andExpect(jsonPath("$._links.conta.href").exists());
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
    @DisplayName("Deve consultar extrato por número da conta em GET /contas/{numero}/extrato (R7)")
    void deveConsultarExtratoPorNumeroContaComSucesso() throws Exception {
        ItemExtratoDTO deposito = new ItemExtratoDTO("2026-10-10T14:30:00", "DEPOSITO", "1000.00", null, null);
        ItemExtratoDTO transferencia = new ItemExtratoDTO("2026-10-12T09:00:00", "TRANSFERENCIA", "350.00",
                new ParteTransferenciaDTO("1291", "12912861012", "Catharyna"),
                new ParteTransferenciaDTO("0950", "09506382000", "Cleuddônio"));
        ExtratoResponseDTO response = new ExtratoResponseDTO(
                "1291",
                "2026-10-01",
                "2026-10-31",
                "800.00",
                List.of(deposito, transferencia)
        );
        response.addLink("self", "http://localhost:8080/contas/1291/extrato");

        when(service.consultarExtrato("1291", "2026-10-01", "2026-10-31", "12912861012", "CLIENTE"))
                .thenReturn(response);

        mockMvc.perform(get("/contas/1291/extrato")
                        .param("inicio", "2026-10-01")
                        .param("fim", "2026-10-31")
                        .header("X-User-CPF", "12912861012")
                        .header("X-User-Tipo", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroConta").value("1291"))
                .andExpect(jsonPath("$.dataInicio").value("2026-10-01"))
                .andExpect(jsonPath("$.dataFim").value("2026-10-31"))
                .andExpect(jsonPath("$.saldoAbertura").value("800.00"))
                .andExpect(jsonPath("$.movimentacoes[0].tipo").value("DEPOSITO"))
                .andExpect(jsonPath("$.movimentacoes[0].valor").value("1000.00"))
                .andExpect(jsonPath("$.movimentacoes[0].origem").doesNotExist())
                .andExpect(jsonPath("$.movimentacoes[1].origem.nome").value("Catharyna"))
                .andExpect(jsonPath("$.movimentacoes[1].destino.numeroConta").value("0950"))
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    @Test
    @DisplayName("Gerente consulta o extrato de qualquer conta (R7)")
    void devePermitirExtratoParaGerente() throws Exception {
        ExtratoResponseDTO response = new ExtratoResponseDTO("1291", "2026-10-01", "2026-10-31", "800.00", List.of());

        when(service.consultarExtrato("1291", null, null, "98574307084", "GERENTE")).thenReturn(response);

        mockMvc.perform(get("/contas/1291/extrato")
                        .header("X-User-CPF", "98574307084")
                        .header("X-User-Tipo", "GERENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoAbertura").value("800.00"));
    }

    @Test
    @DisplayName("Deve buscar conta por número com sucesso")
    void deveBuscarContaPorNumero() throws Exception {
        ContaDTO dto = new ContaDTO("1291", "12912861012", "98574307084");
        dto.setSaldo("800.00");
        dto.setDataCriacao("2000-01-01");
        dto.addLink("self", "http://localhost:8080/contas/1291");

        when(service.buscarContaPorNumero("1291", "12912861012", "CLIENTE")).thenReturn(dto);

        mockMvc.perform(get("/contas/1291")
                        .header("X-User-CPF", "12912861012")
                        .header("X-User-Tipo", "CLIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("1291"))
                .andExpect(jsonPath("$.cpfCliente").value("12912861012"))
                .andExpect(jsonPath("$.cpfGerente").value("98574307084"))
                .andExpect(jsonPath("$.saldo").value("800.00"))
                .andExpect(jsonPath("$.dataCriacao").value("2000-01-01"))
                .andExpect(jsonPath("$._links.self.href").exists());
    }

    @Test
    @DisplayName("Deve retornar 403 quando o cliente consulta a conta de outro cliente")
    void deveRetornar403AoBuscarContaAlheia() throws Exception {
        when(service.buscarContaPorNumero("1291", "09506382000", "CLIENTE"))
                .thenThrow(new AcessoNegadoException("Usuário não autorizado a consultar esta conta."));

        mockMvc.perform(get("/contas/1291")
                        .header("X-User-CPF", "09506382000")
                        .header("X-User-Tipo", "CLIENTE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("Usuário não autorizado a consultar esta conta."));
    }

    @Test
    @DisplayName("Deve retornar 422 ao transferir para a própria conta ou para conta inexistente (R6)")
    void deveRetornar422QuandoRegraDaTransferenciaViolada() throws Exception {
        TransferenciaRequestDTO request = new TransferenciaRequestDTO("1291", "10.00");

        when(service.transferir(eq("1291"), any(TransferenciaRequestDTO.class), eq("12912861012")))
                .thenThrow(new RegraNegocioException("A conta de destino não pode ser igual à conta de origem."));

        mockMvc.perform(post("/contas/1291/transferencia")
                        .header("X-User-CPF", "12912861012")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.erro").value("A conta de destino não pode ser igual à conta de origem."));
    }

    @Test
    @DisplayName("Deve retornar 422 quando o extrato passar de 365 dias (R7)")
    void deveRetornar422QuandoExtratoPassaDe365Dias() throws Exception {
        when(service.consultarExtrato("1291", "2020-01-01", "2023-01-01", "12912861012", "CLIENTE"))
                .thenThrow(new RegraNegocioException("O intervalo do extrato não pode passar de 365 dias."));

        mockMvc.perform(get("/contas/1291/extrato")
                        .param("inicio", "2020-01-01")
                        .param("fim", "2023-01-01")
                        .header("X-User-CPF", "12912861012")
                        .header("X-User-Tipo", "CLIENTE"))
                .andExpect(status().isUnprocessableEntity());
    }
}
