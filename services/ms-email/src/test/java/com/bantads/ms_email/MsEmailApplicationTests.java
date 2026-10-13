package com.bantads.ms_email;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// O application.properties nao e versionado (so o .example), entao o build nao pode
// depender dele: sem spring.mail.host o Spring nem cria o JavaMailSender e o contexto
// nao sobe. O que o teste precisa fica declarado aqui.
// auto-startup off: sem broker no build, os listeners nao devem tentar conectar.
@SpringBootTest(properties = {
		"spring.mail.host=localhost",
		"spring.rabbitmq.listener.simple.auto-startup=false",
		"spring.rabbitmq.listener.direct.auto-startup=false"
})
class MsEmailApplicationTests {

	@Test
	void contextLoads() {
	}

}
