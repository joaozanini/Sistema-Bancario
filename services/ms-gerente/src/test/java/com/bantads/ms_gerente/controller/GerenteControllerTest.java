package com.bantads.ms_gerente.controller;

import com.bantads.ms_gerente.dto.AtualizacaoGerenteDTO;
import com.bantads.ms_gerente.dto.GerenteDTO;
import com.bantads.ms_gerente.exception.GerenteNaoEncontradoException;
import com.bantads.ms_gerente.exception.GlobalExceptionHandler;
import com.bantads.ms_gerente.service.GerenteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GerenteControllerTest {

    private static final String CPF = "98574307084";
    private static final String EMAIL = "ger1@bantads.com.br";

    private MockMvc mockMvc;

    @Mock
    private GerenteService service;

    @InjectMocks
    private GerenteController controller;

    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        jsonMapper = JsonMapper.builder().build();
    }

    @Test
    @DisplayName("Deve retornar 200 ao buscar gerente por CPF com _links HATEOAS")
    void deveBuscarGerentePorCpfComLinksHateoas() throws Exception {
        when(service.buscarPorCpf(CPF))
                .thenReturn(Optional.of(new GerenteDTO(CPF, "Geniéve", EMAIL, "41999990001", true)));

        mockMvc.perform(get("/gerentes/" + CPF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(CPF))
                .andExpect(jsonPath("$.nome").value("Geniéve"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.ativo").value(true))
                .andExpect(jsonPath("$._links.self.href").value("http://localhost:8083/gerentes/" + CPF))
                .andExpect(jsonPath("$._links.atualizacao.href").value("http://localhost:8083/gerentes/" + CPF))
                .andExpect(jsonPath("$._links.remocao.href").value("http://localhost:8083/gerentes/" + CPF));
    }

    @Test
    @DisplayName("Deve retornar 404 ao buscar um gerente que não existe")
    void deveRetornar404AoBuscarGerenteInexistente() throws Exception {
        when(service.buscarPorCpf(CPF)).thenReturn(Optional.empty());

        mockMvc.perform(get("/gerentes/" + CPF))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Deve listar gerentes com _links HATEOAS")
    void deveListarGerentesComLinksHateoas() throws Exception {
        when(service.listar()).thenReturn(List.of(new GerenteDTO(CPF, "Geniéve", EMAIL, "41999990001", true)));

        mockMvc.perform(get("/gerentes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cpf").value(CPF))
                .andExpect(jsonPath("$[0]._links.self.href").value("http://localhost:8083/gerentes/" + CPF));
    }

    @Test
    @DisplayName("Deve retornar 200 com o gerente atualizado e _links HATEOAS")
    void deveAtualizarGerente() throws Exception {
        AtualizacaoGerenteDTO dados = new AtualizacaoGerenteDTO("Geniéve Silva", "41988887777");
        when(service.atualizar(CPF, dados))
                .thenReturn(new GerenteDTO(CPF, dados.nome(), EMAIL, dados.telefone(), true));

        mockMvc.perform(atualizacao(dados))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(CPF))
                .andExpect(jsonPath("$.nome").value("Geniéve Silva"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.telefone").value("41988887777"))
                .andExpect(jsonPath("$._links.self.href").value("http://localhost:8083/gerentes/" + CPF))
                .andExpect(jsonPath("$._links.atualizacao.href").value("http://localhost:8083/gerentes/" + CPF))
                .andExpect(jsonPath("$._links.remocao.href").value("http://localhost:8083/gerentes/" + CPF));
    }

    @Test
    @DisplayName("Deve retornar 404 quando o gerente não existe")
    void deveRetornar404QuandoGerenteNaoExiste() throws Exception {
        when(service.atualizar(eq(CPF), any(AtualizacaoGerenteDTO.class)))
                .thenThrow(new GerenteNaoEncontradoException("Gerente não encontrado."));

        mockMvc.perform(atualizacao(new AtualizacaoGerenteDTO("Geniéve", "41988887777")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Gerente não encontrado."));
    }

    @Test
    @DisplayName("Deve retornar 400 quando o nome não é informado")
    void deveRetornar400QuandoNomeEmBranco() throws Exception {
        mockMvc.perform(atualizacao(new AtualizacaoGerenteDTO(" ", "41988887777")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Nome é obrigatório."));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Deve retornar 400 quando o telefone não é informado")
    void deveRetornar400QuandoTelefoneEmBranco() throws Exception {
        mockMvc.perform(atualizacao(new AtualizacaoGerenteDTO("Geniéve", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Telefone é obrigatório."));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Deve retornar 400 quando o corpo tenta trocar o e-mail, que é imutável (R14)")
    void deveRetornar400QuandoEmailDiferente() throws Exception {
        when(service.atualizar(CPF, new AtualizacaoGerenteDTO("Geniéve Silva", "41988887777", "outro@bantads.com.br", null)))
                .thenThrow(new IllegalArgumentException("O e-mail é o login do gerente e não pode ser alterado."));

        mockMvc.perform(put("/gerentes/" + CPF)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"nome": "Geniéve Silva", "telefone": "41988887777", "email": "outro@bantads.com.br"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("O e-mail é o login do gerente e não pode ser alterado."));
    }

    private RequestBuilder atualizacao(AtualizacaoGerenteDTO dados) {
        return put("/gerentes/" + CPF)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(dados));
    }
}
