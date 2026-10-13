package com.bantads.ms_email.service;

import com.bantads.ms_email.dto.ComandoEmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    public static final String TIPO_ENVIAR_SENHA = "email.enviar-senha";
    public static final String TIPO_SOLICITACAO_NAO_EFETUADA = "email.solicitacao-nao-efetuada";

    private final JavaMailSender mailSender;
    private final String remetente;

    public EmailService(JavaMailSender mailSender, @Value("${bantads.email.remetente:}") String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    public boolean sabeTratar(String tipo) {
        return TIPO_ENVIAR_SENHA.equals(tipo) || TIPO_SOLICITACAO_NAO_EFETUADA.equals(tipo);
    }

    public void enviar(ComandoEmail comando) {
        String destinatario = comando.texto("email");
        if (destinatario == null || destinatario.isBlank()) {
            throw new IllegalArgumentException("comando sem e-mail de destino");
        }

        SimpleMailMessage mensagem = new SimpleMailMessage();
        if (!remetente.isBlank()) {
            mensagem.setFrom(remetente);
        }
        mensagem.setTo(destinatario);
        mensagem.setSubject(assunto(comando.tipo()));
        mensagem.setText(corpo(comando));
        mailSender.send(mensagem);
    }

    private String assunto(String tipo) {
        return TIPO_ENVIAR_SENHA.equals(tipo)
                ? "BANTADS - sua conta foi aprovada"
                : "BANTADS - sua solicitacao nao foi efetuada";
    }

    private String corpo(ComandoEmail comando) {
        String nome = comando.texto("nome") == null ? "cliente" : comando.texto("nome");

        if (TIPO_ENVIAR_SENHA.equals(comando.tipo())) {
            return "Ola, %s!%n%nSua conta no BANTADS foi aprovada.%nSua senha de acesso e: %s%n%nRecomendamos trocar a senha apos o primeiro acesso."
                    .formatted(nome, comando.texto("senha"));
        }
        return "Ola, %s!%n%nNao foi possivel efetuar sua solicitacao de abertura de conta no BANTADS.%nEntre em contato conosco para mais informacoes."
                .formatted(nome);
    }
}
