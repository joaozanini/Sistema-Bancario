package com.bantads.ms_orquestrador.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Apaga no Redis as sessoes gravadas pelo API Gateway (R15) */
@Repository
public class SessaoRepository {

    private static final String PREFIXO_SESSAO = "sessao:";
    private static final String PREFIXO_REVERSA = "sessao:cpf:";
    private static final String PREFIXO_REVOGADO = "sessao:revogado:";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public SessaoRepository(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    /** Apaga as sessoes do CPF e devolve true se havia alguma */
    public boolean encerrarPorCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return false;
        }

        Optional<String> jti = Optional.ofNullable(redis.opsForValue().get(chaveReversa(cpf)));
        jti.ifPresent(valor -> redis.delete(chaveSessao(valor)));
        redis.delete(chaveReversa(cpf));

        // apaga tambem as sessoes do CPF que a chave reversa nao aponta
        List<String> restantes = sessoesDoCpf(cpf);
        restantes.forEach(redis::delete);

        return jti.isPresent() || !restantes.isEmpty();
    }

    // percorre sessao:<jti> com SCAN e fica com as do CPF
    private List<String> sessoesDoCpf(String cpf) {
        List<String> chaves = new ArrayList<>();
        ScanOptions opcoes = ScanOptions.scanOptions().match(PREFIXO_SESSAO + "*").count(100).build();
        try (Cursor<String> cursor = redis.scan(opcoes)) {
            while (cursor.hasNext()) {
                String chave = cursor.next();
                if (chave.startsWith(PREFIXO_REVERSA) || chave.startsWith(PREFIXO_REVOGADO)) {
                    continue;
                }
                if (cpf.equals(cpfDaSessao(redis.opsForValue().get(chave)))) {
                    chaves.add(chave);
                }
            }
        }
        return chaves;
    }

    // le o cpf do valor { cpf, tipo } de sessao:<jti>
    private String cpfDaSessao(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return jsonMapper.readValue(valor, Sessao.class).cpf();
        } catch (JacksonException e) {
            return null;
        }
    }

    private String chaveSessao(String jti) {
        return PREFIXO_SESSAO + jti;
    }

    private String chaveReversa(String cpf) {
        return PREFIXO_REVERSA + cpf;
    }

    private record Sessao(String cpf, String tipo) {
    }
}
