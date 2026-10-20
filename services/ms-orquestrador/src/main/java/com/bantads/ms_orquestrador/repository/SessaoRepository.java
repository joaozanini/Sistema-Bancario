package com.bantads.ms_orquestrador.repository;

import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Sessoes gravadas pelo API Gateway no login. O Orquestrador so apaga, nunca
 * cria: na Remocao de Gerente (R15) o acesso do gerente removido tem de cair
 * na hora, sem esperar o TTL de inatividade expirar.
 *
 * O formato das chaves e o mesmo do session.service.js do Gateway; mudar la
 * exige mudar aqui.
 */
@Repository
public class SessaoRepository {

    private final StringRedisTemplate redis;

    public SessaoRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Apaga a sessao do CPF e devolve true se havia alguma. A chave reversa
     * existe justamente para isto: aqui so se conhece o CPF, nunca o jti.
     */
    public boolean encerrarPorCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return false;
        }

        Optional<String> jti = Optional.ofNullable(redis.opsForValue().get(chaveReversa(cpf)));
        jti.ifPresent(valor -> redis.delete(chaveSessao(valor)));
        redis.delete(chaveReversa(cpf));

        return jti.isPresent();
    }

    private String chaveSessao(String jti) {
        return "sessao:" + jti;
    }

    private String chaveReversa(String cpf) {
        return "sessao:cpf:" + cpf;
    }
}
