package com.bantads.ms_email.service;

import com.bantads.ms_email.dto.ComandoEmail;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    public static final String TIPO_ENVIAR_SENHA = "email.enviar-senha";
    public static final String TIPO_SOLICITACAO_NAO_EFETUADA = "email.solicitacao-nao-efetuada";
    public static final String TIPO_TROCA_GERENTE = "email.troca-gerente";
    public static final String TIPO_REMOCAO_GERENTE = "email.remocao-gerente";
    public static final String TIPO_SOLICITACAO_REJEITADA = "email.solicitacao-rejeitada";

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String remetente;

    public EmailService(JavaMailSender mailSender, @Value("${bantads.email.remetente:}") String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    public boolean sabeTratar(String tipo) {
        return TIPO_ENVIAR_SENHA.equals(tipo)
                || TIPO_SOLICITACAO_NAO_EFETUADA.equals(tipo)
                || TIPO_TROCA_GERENTE.equals(tipo)
                || TIPO_REMOCAO_GERENTE.equals(tipo)
                || TIPO_SOLICITACAO_REJEITADA.equals(tipo);
    }

    public void enviar(ComandoEmail comando) {
        if (ehAvisoDeGerente(comando.tipo())) {
            avisarClientes(comando);
            return;
        }

        String destinatario = comando.texto("email");
        if (destinatario == null || destinatario.isBlank()) {
            throw new IllegalArgumentException("comando sem e-mail de destino");
        }

        String nome = comando.texto("nome") == null ? "cliente" : comando.texto("nome");
        despachar(destinatario, assunto(comando.tipo()), corpo(comando.tipo(), nome, comando.texto("senha"), null, comando.texto("motivo")));
    }

    private boolean ehAvisoDeGerente(String tipo) {
        return TIPO_TROCA_GERENTE.equals(tipo) || TIPO_REMOCAO_GERENTE.equals(tipo);
    }

    /**
     * R13 e R15 notificam todos os clientes afetados. Um cliente sem e-mail e
     * registrado e pulado em vez de abortar: interromper no meio faria a
     * retentativa da fila reenviar para quem ja recebeu. So quando nenhum
     * destinatario existe a excecao sobe, porque ai a retentativa tem sentido.
     */
    private void avisarClientes(ComandoEmail comando) {
        List<Map<String, Object>> clientes = comando.lista("clientes");
        String nomeGerente = comando.texto("nomeGerente");
        int enviados = 0;

        for (Map<String, Object> cliente : clientes) {
            String destinatario = valor(cliente, "email");
            if (destinatario == null || destinatario.isBlank()) {
                log.warn("cliente sem e-mail no aviso '{}' (saga {}), pulado", comando.tipo(), comando.sagaId());
                continue;
            }

            String nome = valor(cliente, "nome") == null ? "cliente" : valor(cliente, "nome");
            despachar(destinatario, assunto(comando.tipo()), corpo(comando.tipo(), nome, null, nomeGerente, null));
            enviados++;
        }

        if (enviados == 0) {
            throw new IllegalArgumentException("comando sem nenhum cliente com e-mail de destino");
        }
    }

    private void despachar(String destinatario, String assunto, String corpo) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        if (!remetente.isBlank()) {
            mensagem.setFrom(remetente);
        }
        mensagem.setTo(destinatario);
        mensagem.setSubject(assunto);
        mensagem.setText(corpo);
        mailSender.send(mensagem);
    }

    private String valor(Map<String, Object> cliente, String chave) {
        Object bruto = cliente == null ? null : cliente.get(chave);
        return bruto == null ? null : bruto.toString();
    }

    private String assunto(String tipo) {
        return switch (tipo) {
            case TIPO_ENVIAR_SENHA -> "BANTADS - sua conta foi aprovada";
            case TIPO_TROCA_GERENTE, TIPO_REMOCAO_GERENTE -> "BANTADS - seu gerente de contas mudou";
            case TIPO_SOLICITACAO_REJEITADA -> "BANTADS - sua solicitacao de conta foi recusada";
            default -> "BANTADS - sua solicitacao nao foi efetuada";
        };
    }

    private String corpo(String tipo, String nome, String senha, String nomeGerente, String motivo) {
        return switch (tipo) {
            case TIPO_ENVIAR_SENHA -> ("Ola, %s!%n%nSua conta no BANTADS foi aprovada.%nSua senha de acesso e: %s"
                    + "%n%nRecomendamos trocar a senha apos o primeiro acesso.").formatted(nome, senha);
            case TIPO_TROCA_GERENTE -> ("Ola, %s!%n%nSua conta no BANTADS passou a ser atendida pelo gerente %s."
                    + "%n%nNenhuma acao e necessaria da sua parte.")
                    .formatted(nome, nomeGerente == null ? "responsavel" : nomeGerente);
            case TIPO_REMOCAO_GERENTE -> ("Ola, %s!%n%nSeu gerente de contas no BANTADS foi removido e sua conta "
                    + "foi transferida para outro gerente.%n%nNenhuma acao e necessaria da sua parte.").formatted(nome);
            case TIPO_SOLICITACAO_REJEITADA -> ("Ola, %s!%n%nSua solicitacao de abertura de conta no BANTADS foi recusada."
                    + "%nMotivo: %s").formatted(nome, motivo);
            default -> ("Ola, %s!%n%nNao foi possivel efetuar sua solicitacao de abertura de conta no BANTADS."
                    + "%nEntre em contato conosco para mais informacoes.").formatted(nome);
        };
    }
}
