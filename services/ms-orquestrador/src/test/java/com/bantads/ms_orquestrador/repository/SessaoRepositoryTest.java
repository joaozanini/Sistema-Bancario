package com.bantads.ms_orquestrador.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class SessaoRepositoryTest {

    private static final String CPF = "98574307084";
    private static final String JTI = "0f3c9e1a-2b44-4d77-9c10-6a5b8e2f1d03";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);

    private SessaoRepository repository;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valores);
        repository = new SessaoRepository(redis);
    }

    @Test
    void apagaAsDuasChavesQuandoOGerenteEstaLogado() {
        when(valores.get("sessao:cpf:" + CPF)).thenReturn(JTI);

        assertThat(repository.encerrarPorCpf(CPF)).isTrue();

        verify(redis).delete("sessao:" + JTI);
        verify(redis).delete("sessao:cpf:" + CPF);
    }

    @Test
    void apagar_sessao_de_quem_nao_esta_logado_nao_falha() {
        when(valores.get("sessao:cpf:" + CPF)).thenReturn(null);

        assertThat(repository.encerrarPorCpf(CPF)).isFalse();

        // sem jti nao ha chave de sessao para apagar, mas a reversa some do mesmo jeito
        verify(redis, never()).delete("sessao:null");
        verify(redis).delete("sessao:cpf:" + CPF);
    }

    @Test
    void cpfVazioOuNuloNaoToca_noRedis() {
        assertThat(repository.encerrarPorCpf(null)).isFalse();
        assertThat(repository.encerrarPorCpf("   ")).isFalse();

        verifyNoInteractions(valores);
        verify(redis, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void usa_o_mesmo_formato_de_chave_do_gateway() {
        when(valores.get("sessao:cpf:" + CPF)).thenReturn(JTI);

        repository.encerrarPorCpf(CPF);

        // session.service.js grava exatamente estas duas chaves no login
        verify(valores).get("sessao:cpf:" + CPF);
        verify(redis).delete("sessao:" + JTI);
    }
}
