package com.bantads.ms_orquestrador;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// auto-startup off: sem broker no build, os listeners nao devem tentar conectar.
// O override vai aqui, e nao num application.properties de teste — esse sombrearia
// o arquivo de producao inteiro e os testes validariam a config errada.
@SpringBootTest(properties = {
		"spring.rabbitmq.listener.simple.auto-startup=false",
		"spring.rabbitmq.listener.direct.auto-startup=false"
})
class MsOrquestradorApplicationTests {

	@Test
	void contextLoads() {
	}

}
