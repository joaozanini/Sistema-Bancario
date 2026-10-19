package com.bantads.ms_orquestrador;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// auto-startup off: sem broker no build, os listeners nao devem tentar conectar
// pelo mesmo motivo o agendador de timeouts nao consulta o Redis.
// O override vai aqui, e nao num application.properties de teste — esse sombrearia
// o arquivo de producao inteiro e os testes validariam a config errada.
// os timeouts vem do application.properties, que nao e versionado — sem declarar
// aqui o contexto nao sobe num clone limpo (placeholder 'saga.timeout-passo')
@SpringBootTest(properties = {
		"spring.rabbitmq.listener.simple.auto-startup=false",
		"spring.rabbitmq.listener.direct.auto-startup=false",
		"saga.timeout-passo=30s",
		"saga.timeout.intervalo-verificacao=1s",
		"saga.timeout.verificar=false"
})
class MsOrquestradorApplicationTests {

	@Test
	void contextLoads() {
	}

}
