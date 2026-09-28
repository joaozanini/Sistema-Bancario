package com.bantads.ms_orquestrador.messaging;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.bantads.ms_orquestrador.dto.MensagemSaga;
import com.bantads.ms_orquestrador.dto.StatusResposta;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MensagemSagaCodecTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final MensagemSagaCodec codec = new MensagemSagaCodec(jsonMapper);

    @Test
    void leJsonCruPublicadoPeloGatewaySemTypeId() {
        String json = """
                {"sagaId":"3f1c2a7e-0000-4000-8000-000000000001","tipo":"aprovar-cliente",\
                "timestamp":"2026-04-30T10:00:00","payload":{"cpf":"12912861012"}}""";
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType("application/json");

        MensagemSaga mensagem = codec.ler(new Message(json.getBytes(StandardCharsets.UTF_8), propriedades));

        assertThat(propriedades.getHeaders()).doesNotContainKey("__TypeId__");
        assertThat(mensagem.sagaId()).isEqualTo("3f1c2a7e-0000-4000-8000-000000000001");
        assertThat(mensagem.tipo()).isEqualTo("aprovar-cliente");
        assertThat(mensagem.timestamp()).isEqualTo("2026-04-30T10:00:00");
        assertThat(mensagem.payload()).containsExactlyEntriesOf(Map.of("cpf", "12912861012"));
        assertThat(mensagem.status()).isNull();
    }

    @Test
    void leRespostaComStatusEErro() {
        String json = """
                {"sagaId":"s1","tipo":"auth.criar-usuario","timestamp":"2026-04-30T10:00:05",\
                "payload":{},"status":"FALHA","erro":"LOGIN_DUPLICADO"}""";

        MensagemSaga mensagem = codec.ler(new Message(json.getBytes(StandardCharsets.UTF_8)));

        assertThat(mensagem.status()).isEqualTo(StatusResposta.FALHA);
        assertThat(mensagem.erro()).isEqualTo("LOGIN_DUPLICADO");
    }

    @Test
    void escreveComandoSemCamposNulosESemTypeId() {
        Message message = codec.escrever(MensagemSaga.comando("s1", "cliente.aprovar-solicitacao",
                "2026-04-30T10:00:00", Map.of("cpf", "12912861012")));

        JsonNode json = jsonMapper.readTree(message.getBody());
        assertThat(json.propertyNames()).containsExactly("sagaId", "tipo", "timestamp", "payload");
        assertThat(json.get("timestamp").asString()).isEqualTo("2026-04-30T10:00:00");
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getHeaders()).doesNotContainKey("__TypeId__");
    }
}
