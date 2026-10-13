package com.bantads.ms_gerente.service;

import com.bantads.ms_gerente.dto.AtualizacaoGerenteDTO;
import com.bantads.ms_gerente.dto.GerenteDTO;
import com.bantads.ms_gerente.exception.GerenteNaoEncontradoException;
import com.bantads.ms_gerente.model.Gerente;
import com.bantads.ms_gerente.repository.GerenteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GerenteServiceTest {

    private static final String CPF = "98574307084";

    @Mock
    private GerenteRepository repository;

    @InjectMocks
    private GerenteService service;

    @Test
    @DisplayName("Deve atualizar nome e telefone mantendo CPF, e-mail e situação")
    void deveAtualizarDadosDoGerente() {
        Gerente gerente = gerenteExistente();
        when(repository.findById(CPF)).thenReturn(Optional.of(gerente));
        when(repository.save(gerente)).thenReturn(gerente);

        GerenteDTO atualizado = service.atualizar(CPF,
                new AtualizacaoGerenteDTO("Geniéve Silva", "41988887777"));

        assertThat(atualizado).isEqualTo(
                new GerenteDTO(CPF, "Geniéve Silva", "ger1@bantads.com.br", "41988887777", true));
    }

    @Test
    @DisplayName("Deve lançar exceção quando o gerente não existe")
    void deveFalharQuandoGerenteNaoExiste() {
        when(repository.findById(CPF)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(CPF,
                new AtualizacaoGerenteDTO("Geniéve", null)))
                .isInstanceOf(GerenteNaoEncontradoException.class);

        verify(repository, never()).save(any());
    }

    private Gerente gerenteExistente() {
        Gerente gerente = new Gerente();
        gerente.setCpf(CPF);
        gerente.setNome("Geniéve");
        gerente.setEmail("ger1@bantads.com.br");
        gerente.setTelefone("41999990001");
        return gerente;
    }
}
