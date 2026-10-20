package sistema.eventos.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sistema.eventos.model.Usuario;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.admin-notification-email:}")
    private String adminNotificationEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void enviarSolicitacaoParaAdmin(Usuario usuario) {
        if (adminNotificationEmail == null || adminNotificationEmail.isBlank()) {
            logger.info("Notificação por e-mail não enviada: destinatário administrativo não configurado.");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(adminNotificationEmail);
        message.setSubject("🚨 SEPLAG - Nova Solicitação de Organizador");

        String corpoEmail = "Olá Admin,\n\n" +
                "O usuário " + usuario.getNome() + " (" + usuario.getEmail() + ") solicitou permissão de Organizador.\n\n" +
                "A solicitação aguarda análise por um administrador autenticado da plataforma.\n\n" +
                "Atenciosamente,\n" +
                "Sistema de Eventos SEPLAG";
        message.setText(corpoEmail);

        try {
            mailSender.send(message);
            logger.info("Notificação de solicitação de organizador enviada.");

        } catch (Exception e) {
            logger.warn("Falha ao enviar notificação de solicitação de organizador.", e);
        }
    }

    public void enviarSolicitacaoAprovada(Usuario usuario) {
        if (usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            logger.info("Notificação de aprovação não enviada: e-mail do usuário não informado.");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(usuario.getEmail());
        message.setSubject("✅ SEPLAG - Solicitação de Organizador Aprovada");

        String corpoEmail = "Olá " + usuario.getNome() + ",\n\n"
                + "Sua solicitação de acesso de Organizador foi APROVADA.\n\n"
                + "Você agora possui acesso ao painel de organizador da plataforma.\n\n"
                + "Atenciosamente,\n"
                + "Sistema de Eventos SEPLAG";
        message.setText(corpoEmail);

        try {
            mailSender.send(message);
            logger.info("Notificação de aprovação de organizador enviada para {}.", usuario.getEmail());

        } catch (Exception e) {
            logger.warn("Falha ao enviar notificação de aprovação de organizador.", e);
        }
    }

    public void enviarSolicitacaoRejeitada(Usuario usuario) {
        if (usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            logger.info("Notificação de rejeição não enviada: e-mail do usuário não informado.");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(usuario.getEmail());
        message.setSubject("❌ SEPLAG - Solicitação de Organizador Rejeitada");

        String corpoEmail = "Olá " + usuario.getNome() + ",\n\n"
                + "Sua solicitação de acesso de Organizador foi REJEITADA.\n\n"
                + "Você continua com o perfil de PARTICIPANTE na plataforma.\n\n"
                + "Atenciosamente,\n"
                + "Sistema de Eventos SEPLAG";
        message.setText(corpoEmail);

        try {
            mailSender.send(message);
            logger.info("Notificação de rejeição de organizador enviada para {}.", usuario.getEmail());

        } catch (Exception e) {
            logger.warn("Falha ao enviar notificação de rejeição de organizador.", e);
        }
    }
}
