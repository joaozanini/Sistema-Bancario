package com.bantads.ms_email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bantads.ms_email.dto.ComandoEmail;
import java.util.List;
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
    void trataOsQuatroTiposQueOOrquestradorPublica() {
        assertThat(service.sabeTratar(EmailService.TIPO_ENVIAR_SENHA)).isTrue();
        assertThat(service.sabeTratar(EmailService.TIPO_SOLICITACAO_NAO_EFETUADA)).isTrue();
        assertThat(service.sabeTratar(EmailService.TIPO_TROCA_GERENTE)).isTrue();
        assertThat(service.sabeTratar(EmailService.TIPO_REMOCAO_GERENTE)).isTrue();
        assertThat(service.sabeTratar("email.qualquer-outro")).isFalse();
        assertThat(service.sabeTratar(null)).isFalse();
    }

    private List<SimpleMailMessage> capturarEnvios(int quantos) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, org.mockito.Mockito.times(quantos)).send(captor.capture());
        return captor.getAllValues();
    }

    private static Map<String, Object> cliente(String nome, String email) {
        Map<String, Object> mapa = new java.util.HashMap<>();
        mapa.put("nome", nome);
        mapa.put("email", email);
        return mapa;
    }

    @Test
    void trocaDeGerenteAvisaTodosOsClientesComONomeDoNovoGerente() {
        service.enviar(comando(EmailService.TIPO_TROCA_GERENTE, Map.of(
                "nomeGerente", "Genieve",
                "clientes", List.of(
                        cliente("Catharyna", "cli1@bantads.com.br"),
                        cliente("Cutardo", "cli2@bantads.com.br")))));

        List<SimpleMailMessage> enviadas = capturarEnvios(2);
        assertThat(enviadas).extracting(m -> m.getTo()[0])
                .containsExactly("cli1@bantads.com.br", "cli2@bantads.com.br");
        assertThat(enviadas.get(0).getSubject()).contains("gerente");
        assertThat(enviadas.get(0).getText()).contains("Catharyna").contains("Genieve");
        assertThat(enviadas.get(1).getText()).contains("Cutardo").contains("Genieve");
    }

    @Test
    void remocaoDeGerenteAvisaTodosSemCitarNomeDeGerente() {
        service.enviar(comando(EmailService.TIPO_REMOCAO_GERENTE, Map.of(
                "clientes", List.of(cliente("Catharyna", "cli1@bantads.com.br")))));

        SimpleMailMessage enviada = capturarEnvio();
        assertThat(enviada.getTo()).containsExactly("cli1@bantads.com.br");
        assertThat(enviada.getText()).contains("Catharyna").contains("transferida");
    }

    @Test
    void clienteSemEmailEPuladoSemAbortarOsDemais() {
        service.enviar(comando(EmailService.TIPO_REMOCAO_GERENTE, Map.of(
                "clientes", List.of(
                        cliente("Sem Email", ""),
                        cliente("Catharyna", "cli1@bantads.com.br")))));

        // um destinatario invalido nao pode impedir o aviso de quem tem e-mail:
        // abortar no meio faria a retentativa reenviar para quem ja recebeu
        assertThat(capturarEnvios(1).get(0).getTo()).containsExactly("cli1@bantads.com.br");
    }

    @Test
    void avisoSemNenhumClienteComEmailFalhaSemChamarOSmtp() {
        assertThatThrownBy(() -> service.enviar(comando(EmailService.TIPO_REMOCAO_GERENTE,
                Map.of("clientes", List.of(cliente("Sem Email", ""))))))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mailSender);
    }

    @Test
    void avisoComListaVaziaFalha() {
        assertThatThrownBy(() -> service.enviar(comando(EmailService.TIPO_TROCA_GERENTE, Map.of())))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mailSender);
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
