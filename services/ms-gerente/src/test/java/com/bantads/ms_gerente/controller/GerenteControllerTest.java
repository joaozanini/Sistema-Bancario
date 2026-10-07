package com.bantads.ms_gerente.controller;

import com.bantads.ms_gerente.dto.AtualizacaoGerenteDTO;
import com.bantads.ms_gerente.dto.GerenteDTO;
import com.bantads.ms_gerente.exception.EmailJaCadastradoException;
import com.bantads.ms_gerente.exception.GerenteNaoEncontradoException;
import com.bantads.ms_gerente.exception.GlobalExceptionHandler;
import com.bantads.ms_gerente.repository.GerenteRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GerenteControllerTest {

    private static final String CPF = "98574307084";

    private MockMvc mockMvc;

    @Mock
    private GerenteRepository repository;

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
    @DisplayName("Deve retornar 200 com o gerente atualizado")
    void deveAtualizarGerente() throws Exception {
        AtualizacaoGerenteDTO dados = new AtualizacaoGerenteDTO("Geniéve Silva", "novo@bantads.com.br", "41988887777");
        when(service.atualizar(CPF, dados))
                .thenReturn(new GerenteDTO(CPF, dados.nome(), dados.email(), dados.telefone(), true));

        mockMvc.perform(atualizacao(dados))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(CPF))
                .andExpect(jsonPath("$.nome").value("Geniéve Silva"))
                .andExpect(jsonPath("$.email").value("novo@bantads.com.br"))
                .andExpect(jsonPath("$.telefone").value("41988887777"));
    }

    @Test
    @DisplayName("Deve retornar 404 quando o gerente não existe")
    void deveRetornar404QuandoGerenteNaoExiste() throws Exception {
        when(service.atualizar(eq(CPF), any(AtualizacaoGerenteDTO.class)))
                .thenThrow(new GerenteNaoEncontradoException("Gerente não encontrado."));

        mockMvc.perform(atualizacao(new AtualizacaoGerenteDTO("Geniéve", "ger1@bantads.com.br", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").value("Gerente não encontrado."));
    }

    @Test
    @DisplayName("Deve retornar 409 quando o e-mail pertence a outro gerente")
    void deveRetornar409QuandoEmailJaCadastrado() throws Exception {
        when(service.atualizar(eq(CPF), any(AtualizacaoGerenteDTO.class)))
                .thenThrow(new EmailJaCadastradoException("E-mail já cadastrado para outro gerente."));

        mockMvc.perform(atualizacao(new AtualizacaoGerenteDTO("Geniéve", "ger2@bantads.com.br", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("E-mail já cadastrado para outro gerente."));
    }

    @Test
    @DisplayName("Deve retornar 400 quando o nome não é informado")
    void deveRetornar400QuandoNomeEmBranco() throws Exception {
        mockMvc.perform(atualizacao(new AtualizacaoGerenteDTO(" ", "ger1@bantads.com.br", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Nome é obrigatório."));

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Deve retornar 400 quando o e-mail é inválido")
    void deveRetornar400QuandoEmailInvalido() throws Exception {
        mockMvc.perform(atualizacao(new AtualizacaoGerenteDTO("Geniéve", "sem-arroba", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("E-mail inválido."));

        verifyNoInteractions(service);
    }

    private RequestBuilder atualizacao(AtualizacaoGerenteDTO dados) {
        return put("/gerentes/" + CPF)
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(dados));
    }
}
