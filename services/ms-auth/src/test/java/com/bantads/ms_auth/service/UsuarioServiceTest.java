package com.bantads.ms_auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bantads.ms_auth.exception.CpfJaCadastradoException;
import com.bantads.ms_auth.exception.LoginJaCadastradoException;
import com.bantads.ms_auth.model.TipoUsuario;
import com.bantads.ms_auth.model.Usuario;
import com.bantads.ms_auth.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

class UsuarioServiceTest {

    private static final String SAGA_ID = "3f1c2a7e-0000-4000-8000-000000000001";
    private static final String CPF = "11122233344";
    private static final String EMAIL = "novo@bantads.com.br";
    private static final String SENHA = "Ab3dEf7h";

    private final UsuarioRepository repository = mock(UsuarioRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final GeradorSenha geradorSenha = mock(GeradorSenha.class);

    private UsuarioService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        when(geradorSenha.gerar()).thenReturn(SENHA);
        when(repository.findByCpf(anyString())).thenReturn(Optional.empty());
        service = new UsuarioService(repository, passwordEncoder, geradorSenha);
    }

    @Test
    void criarClienteGravaSoOHashEDevolveASenha() {
        String senha = service.criarCliente(SAGA_ID, CPF, EMAIL);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repository).insert(captor.capture());
        Usuario criado = captor.getValue();
        assertThat(senha).isEqualTo(SENHA);
        assertThat(criado.getSenhaHash()).isEqualTo("hash:" + SENHA).isNotEqualTo(SENHA);
        assertThat(criado.getCpf()).isEqualTo(CPF);
        assertThat(criado.getLogin()).isEqualTo(EMAIL);
        assertThat(criado.getTipo()).isEqualTo(TipoUsuario.CLIENTE);
        assertThat(criado.isAtivo()).isTrue();
        assertThat(criado.getSagaCriacao()).isEqualTo(SAGA_ID);
    }

    @Test
    void loginExistenteRecusaSemGravar() {
        when(repository.existsByLogin(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.criarCliente(SAGA_ID, CPF, EMAIL))
                .isInstanceOf(LoginJaCadastradoException.class);
        verify(repository, never()).insert(any(Usuario.class));
    }

    @Test
    void cpfDeOutroRegistroRecusa() {
        Usuario existente = new Usuario(CPF, TipoUsuario.GERENTE, "ger@bantads.com.br", "hash");
        when(repository.findByCpf(CPF)).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.criarCliente(SAGA_ID, CPF, EMAIL))
                .isInstanceOf(CpfJaCadastradoException.class);
        verify(repository, never()).insert(any(Usuario.class));
        verify(repository, never()).save(any(Usuario.class));
    }

    @Test
    void registroDaMesmaSagaRecebeNovaSenha() {
        Usuario existente = new Usuario(CPF, TipoUsuario.CLIENTE, EMAIL, "hash:antiga");
        existente.setSagaCriacao(SAGA_ID);
        when(repository.findByCpf(CPF)).thenReturn(Optional.of(existente));

        String senha = service.criarCliente(SAGA_ID, CPF, EMAIL);

        assertThat(senha).isEqualTo(SENHA);
        assertThat(existente.getSenhaHash()).isEqualTo("hash:" + SENHA);
        verify(repository).save(existente);
        verify(repository, never()).insert(any(Usuario.class));
    }

    @Test
    void corridaNoIndiceDeLoginViraLoginDuplicado() {
        when(repository.existsByLogin(EMAIL)).thenReturn(false, true);
        when(repository.insert(any(Usuario.class))).thenThrow(new DuplicateKeyException("login"));

        assertThatThrownBy(() -> service.criarCliente(SAGA_ID, CPF, EMAIL))
                .isInstanceOf(LoginJaCadastradoException.class);
    }

    @Test
    void corridaNoIndiceDeCpfViraCpfDuplicado() {
        when(repository.existsByLogin(EMAIL)).thenReturn(false, false);
        when(repository.insert(any(Usuario.class))).thenThrow(new DuplicateKeyException("cpf"));

        assertThatThrownBy(() -> service.criarCliente(SAGA_ID, CPF, EMAIL))
                .isInstanceOf(CpfJaCadastradoException.class);
    }

    @Test
    void compensacaoRemoveOQueAMesmaSagaCriou() {
        Usuario criado = new Usuario(CPF, TipoUsuario.CLIENTE, EMAIL, "hash");
        criado.setSagaCriacao(SAGA_ID);
        when(repository.findByCpf(CPF)).thenReturn(Optional.of(criado));

        service.removerCriadoPelaSaga(SAGA_ID, CPF);

        verify(repository).delete(criado);
    }

    @Test
    void compensacaoPreservaRegistroDeOutraOrigem() {
        Usuario seed = new Usuario(CPF, TipoUsuario.CLIENTE, EMAIL, "hash");
        when(repository.findByCpf(CPF)).thenReturn(Optional.of(seed));

        service.removerCriadoPelaSaga(SAGA_ID, CPF);

        verify(repository, never()).delete(any(Usuario.class));
    }
}
