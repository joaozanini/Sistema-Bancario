package com.bantads.ms_gerente.service;

import com.bantads.ms_gerente.dto.AtualizacaoGerenteDTO;
import com.bantads.ms_gerente.dto.GerenteDTO;
import com.bantads.ms_gerente.exception.CpfJaCadastradoException;
import com.bantads.ms_gerente.exception.EmailJaCadastradoException;
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
    @DisplayName("Deve atualizar nome e telefone mantendo CPF, e-mail, situação e _links HATEOAS")
    void deveAtualizarDadosDoGerente() {
        Gerente gerente = gerenteExistente();
        when(repository.findById(CPF)).thenReturn(Optional.of(gerente));
        when(repository.save(gerente)).thenReturn(gerente);

        GerenteDTO atualizado = service.atualizar(CPF,
                new AtualizacaoGerenteDTO("Geniéve Silva", "41988887777"));

        assertThat(atualizado).isEqualTo(
                new GerenteDTO(CPF, "Geniéve Silva", "ger1@bantads.com.br", "41988887777", true));
        assertThat(atualizado._links()).isNotNull();
        assertThat(atualizado._links().get("self")).containsEntry("href", "http://localhost:8083/gerentes/" + CPF);
        assertThat(atualizado._links().get("gerentes")).containsEntry("href", "http://localhost:8083/gerentes");
        assertThat(atualizado._links().get("atualizar")).containsEntry("href", "http://localhost:8083/gerentes/" + CPF);
        assertThat(atualizado._links().get("remover")).containsEntry("href", "http://localhost:8083/gerentes/" + CPF);
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

    @Test
    @DisplayName("Deve inserir gerente quando CPF e e-mail são únicos")
    void deveInserirGerenteQuandoCpfEEmailUnicos() {
        Gerente novo = new Gerente();
        novo.setCpf("11144477735");
        novo.setNome("Gerente Novo");
        novo.setEmail("novo@bantads.com.br");
        novo.setTelefone("41999990000");

        when(repository.existsById("11144477735")).thenReturn(false);
        when(repository.existsByCpf("11144477735")).thenReturn(false);
        when(repository.existsByEmail("novo@bantads.com.br")).thenReturn(false);
        when(repository.existsByEmailIgnoreCase("novo@bantads.com.br")).thenReturn(false);
        when(repository.save(any(Gerente.class))).thenAnswer(inv -> inv.getArgument(0));

        GerenteDTO criado = service.inserir(novo);

        assertThat(criado.cpf()).isEqualTo("11144477735");
        assertThat(criado.email()).isEqualTo("novo@bantads.com.br");
        assertThat(criado._links()).containsKeys("self", "gerentes", "atualizar", "remover");
    }

    @Test
    @DisplayName("Deve recusar inserção quando CPF já está cadastrado no MS Gerente")
    void deveRecusarInsercaoQuandoCpfDuplicado() {
        Gerente novo = new Gerente();
        novo.setCpf(CPF);
        novo.setNome("Outro Gerente");
        novo.setEmail("outro@bantads.com.br");

        when(repository.existsById(CPF)).thenReturn(true);

        assertThatThrownBy(() -> service.inserir(novo))
                .isInstanceOf(CpfJaCadastradoException.class);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve recusar inserção quando e-mail já está cadastrado no MS Gerente (inclusive case-insensitive)")
    void deveRecusarInsercaoQuandoEmailDuplicado() {
        Gerente novo = new Gerente();
        novo.setCpf("11144477735");
        novo.setNome("Outro Gerente");
        novo.setEmail("GER1@bantads.com.br");

        when(repository.existsById("11144477735")).thenReturn(false);
        when(repository.existsByCpf("11144477735")).thenReturn(false);
        when(repository.existsByEmail("GER1@bantads.com.br")).thenReturn(false);
        when(repository.existsByEmailIgnoreCase("GER1@bantads.com.br")).thenReturn(true);

        assertThatThrownBy(() -> service.inserir(novo))
                .isInstanceOf(EmailJaCadastradoException.class);

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
