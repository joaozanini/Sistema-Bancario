package com.bantads.ms_email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bantads.ms_email.dto.ComandoEmail;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailServiceTest {

    private JavaMailSender mailSender;
    private EmailService service;

    @BeforeEach
    void preparar() {
        mailSender = mock(JavaMailSender.class);
        service = new EmailService(mailSender, "bantads@gmail.com");
    }

    private SimpleMailMessage capturarEnvio() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private static ComandoEmail comando(String tipo, Map<String, Object> payload) {
        return new ComandoEmail("saga-1", tipo, "2026-10-12T10:00:00", payload);
    }

    @Test
    void trataApenasOsDoisTiposDaSagaAprovarCliente() {
        assertThat(service.sabeTratar(EmailService.TIPO_ENVIAR_SENHA)).isTrue();
        assertThat(service.sabeTratar(EmailService.TIPO_SOLICITACAO_NAO_EFETUADA)).isTrue();
        assertThat(service.sabeTratar("email.qualquer-outro")).isFalse();
        assertThat(service.sabeTratar(null)).isFalse();
    }

    @Test
    void enviaSenhaParaOClienteAprovado() {
        service.enviar(comando(EmailService.TIPO_ENVIAR_SENHA,
                Map.of("email", "cli1@bantads.com.br", "nome", "Catharyna", "senha", "aB3xK9")));

        SimpleMailMessage enviada = capturarEnvio();
        assertThat(enviada.getTo()).containsExactly("cli1@bantads.com.br");
        assertThat(enviada.getFrom()).isEqualTo("bantads@gmail.com");
        assertThat(enviada.getSubject()).contains("aprovada");
        assertThat(enviada.getText()).contains("Catharyna").contains("aB3xK9");
    }

    @Test
    void avisaQuandoASolicitacaoNaoFoiEfetuada() {
        service.enviar(comando(EmailService.TIPO_SOLICITACAO_NAO_EFETUADA,
                Map.of("email", "cli9@bantads.com.br", "nome", "Cutardo")));

        SimpleMailMessage enviada = capturarEnvio();
        assertThat(enviada.getTo()).containsExactly("cli9@bantads.com.br");
        assertThat(enviada.getSubject()).contains("nao foi efetuada");
        assertThat(enviada.getText()).contains("Cutardo").doesNotContain("senha");
    }

    @Test
    void comandoSemDestinatarioFalhaSemChamarOSmtp() {
        assertThatThrownBy(() -> service.enviar(comando(EmailService.TIPO_ENVIAR_SENHA, Map.of("nome", "X"))))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mailSender);
    }

    @Test
    void usaNomeGenericoQuandoOPayloadNaoTrazNome() {
        service.enviar(comando(EmailService.TIPO_SOLICITACAO_NAO_EFETUADA,
                Map.of("email", "sem.nome@bantads.com.br")));

        assertThat(capturarEnvio().getText()).contains("cliente");
    }

    @Test
    void naoEnviaNadaQuandoOTipoENulo() {
        assertThat(service.sabeTratar(null)).isFalse();
        verify(mailSender, org.mockito.Mockito.never()).send(any(SimpleMailMessage.class));
    }
}
