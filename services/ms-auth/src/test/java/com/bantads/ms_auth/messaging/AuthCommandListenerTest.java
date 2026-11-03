package com.bantads.ms_auth.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bantads.ms_auth.dto.MensagemSaga;
import com.bantads.ms_auth.dto.StatusResposta;
import com.bantads.ms_auth.service.ComandoSagaService;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AuthCommandListenerTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final ComandoSagaService service = mock(ComandoSagaService.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    private final AuthCommandListener listener = new AuthCommandListener(jsonMapper, service, rabbitTemplate);

    @Test
    void respostaVaiParaOrquestradorReplyNoFormatoDoEnvelope() {
        when(service.processar(any())).thenReturn(Optional.of(
                MensagemSaga.sucesso("saga-1", "auth.criar-usuario", "2026-04-30T10:00:00", Map.of("senha", "x"))));

        listener.onComando(mensagem("""
                {"sagaId":"saga-1","tipo":"auth.criar-usuario","timestamp":"2026-04-30T10:00:00",
                 "payload":{"cpf":"11122233344","email":"novo@bantads.com.br","tipo":"CLIENTE"}}
                """));

        ArgumentCaptor<MensagemSaga> lido = ArgumentCaptor.forClass(MensagemSaga.class);
        verify(service).processar(lido.capture());
        assertThat(lido.getValue().payload()).containsEntry("email", "novo@bantads.com.br");

        ArgumentCaptor<Message> enviada = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), eq("orquestrador.reply"), enviada.capture());
        JsonNode json = jsonMapper.readTree(enviada.getValue().getBody());
        assertThat(json.get("status").asString()).isEqualTo(StatusResposta.SUCESSO.name());
        assertThat(json.get("sagaId").asString()).isEqualTo("saga-1");
        assertThat(json.has("erro")).isFalse();
        assertThat(enviada.getValue().getMessageProperties().getContentType())
                .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    }

    @Test
    void semRespostaNadaEPublicado() {
        when(service.processar(any())).thenReturn(Optional.empty());

        listener.onComando(mensagem("{\"sagaId\":\"saga-1\",\"tipo\":\"auth.criar-usuario\"}"));

        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void jsonInvalidoEDescartadoSemExcecao() {
        listener.onComando(mensagem("{nao e json"));

        verify(service, never()).processar(any());
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    private static Message mensagem(String corpo) {
        return new Message(corpo.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
