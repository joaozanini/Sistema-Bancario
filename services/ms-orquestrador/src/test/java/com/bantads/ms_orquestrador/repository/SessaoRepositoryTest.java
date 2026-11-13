package com.bantads.ms_orquestrador.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

class SessaoRepositoryTest {

    private static final String CPF = "98574307084";
    private static final String OUTRO_CPF = "64065268052";
    private static final String JTI = "0f3c9e1a-2b44-4d77-9c10-6a5b8e2f1d03";
    private static final String JTI_ANTIGO = "7b1d2c3e-4f50-4a61-8b72-9c0d1e2f3a4b";
    private static final String JTI_DE_OUTRO = "c4d5e6f7-0819-4a2b-b3c4-d5e6f7a8b9c0";

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);

    private SessaoRepository repository;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valores);
        chavesNoRedis();
        repository = new SessaoRepository(redis, JsonMapper.builder().build());
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
        verify(redis, never()).scan(any(ScanOptions.class));
    }

    @Test
    void usa_o_mesmo_formato_de_chave_do_gateway() {
        when(valores.get("sessao:cpf:" + CPF)).thenReturn(JTI);

        repository.encerrarPorCpf(CPF);

        // session.service.js grava exatamente estas duas chaves no login
        verify(valores).get("sessao:cpf:" + CPF);
        verify(redis).delete("sessao:" + JTI);
    }

    @Test
    void apagaTambemASessaoMaisAntigaQueAChaveReversaNaoAponta() {
        // dois logins do mesmo gerente: a reversa ficou com o jti do segundo
        when(valores.get("sessao:cpf:" + CPF)).thenReturn(JTI);
        chavesNoRedis("sessao:" + JTI_ANTIGO, "sessao:cpf:" + OUTRO_CPF, "sessao:" + JTI_DE_OUTRO, "sessao:revogado:" + JTI);
        when(valores.get("sessao:" + JTI_ANTIGO)).thenReturn("{\"cpf\":\"" + CPF + "\",\"tipo\":\"GERENTE\"}");
        when(valores.get("sessao:" + JTI_DE_OUTRO)).thenReturn("{\"cpf\":\"" + OUTRO_CPF + "\",\"tipo\":\"GERENTE\"}");

        assertThat(repository.encerrarPorCpf(CPF)).isTrue();

        verify(redis).delete("sessao:" + JTI);
        verify(redis).delete("sessao:" + JTI_ANTIGO);
        // sessao, chave reversa e revogados dos outros usuarios ficam como estao
        verify(redis, never()).delete("sessao:" + JTI_DE_OUTRO);
        verify(redis, never()).delete("sessao:cpf:" + OUTRO_CPF);
        verify(redis, never()).delete("sessao:revogado:" + JTI);
    }

    @Test
    void encerraASessaoQueFicouSemChaveReversa() {
        // o logout de uma sessao apaga a reversa; a outra sessao do mesmo CPF continua aberta
        when(valores.get("sessao:cpf:" + CPF)).thenReturn(null);
        chavesNoRedis("sessao:" + JTI_ANTIGO);
        when(valores.get("sessao:" + JTI_ANTIGO)).thenReturn("{\"cpf\":\"" + CPF + "\",\"tipo\":\"GERENTE\"}");

        assertThat(repository.encerrarPorCpf(CPF)).isTrue();

        verify(redis).delete("sessao:" + JTI_ANTIGO);
    }

    @Test
    void ignoraChaveQueExpirouDuranteAVarreduraOuQueNaoEJson() {
        when(valores.get("sessao:cpf:" + CPF)).thenReturn(null);
        chavesNoRedis("sessao:" + JTI_ANTIGO, "sessao:" + JTI_DE_OUTRO);
        when(valores.get("sessao:" + JTI_ANTIGO)).thenReturn(null);
        when(valores.get("sessao:" + JTI_DE_OUTRO)).thenReturn("nao e json");

        assertThat(repository.encerrarPorCpf(CPF)).isFalse();

        verify(redis, never()).delete("sessao:" + JTI_ANTIGO);
        verify(redis, never()).delete("sessao:" + JTI_DE_OUTRO);
    }

    // o SCAN de sessao:* devolve estas chaves
    @SuppressWarnings("unchecked")
    private void chavesNoRedis(String... chaves) {
        Iterator<String> iterador = List.of(chaves).iterator();
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenAnswer(inv -> iterador.hasNext());
        when(cursor.next()).thenAnswer(inv -> iterador.next());
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
    }
}
