package com.bantads.ms_auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// sem broker no build, o consumidor de ms.auth.cmd nao deve tentar conectar
@SpringBootTest(properties = {
		"spring.rabbitmq.listener.simple.auto-startup=false",
		"spring.rabbitmq.listener.direct.auto-startup=false"
})
class MsAuthApplicationTests {

	@Test
	void contextLoads() {
	}

}
