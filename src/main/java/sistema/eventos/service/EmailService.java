package sistema.eventos.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import sistema.eventos.model.Usuario;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void enviarSolicitacaoParaAdmin(Usuario usuario, Long solicitacaoId) {
        String urlAprovação = "http://localhost:8080/login?aprovar=" + solicitacaoId;

        SimpleMailMessage message = new SimpleMailMessage();

        // 🎯 Unificado: O mesmo e-mail da SEPLAG configurado como destinatário (Admin)
        message.setTo("ryanmendes@seplag.mt.gov.br");
        message.setSubject("🚨 SEPLAG - Nova Solicitação de Organizador");

        String corpoEmail = "Olá Admin,\n\n" +
                "O usuário " + usuario.getNome() + " (" + usuario.getEmail() + ") solicitou permissão de Organizador.\n\n" +
                "👉 Para aprovar o perfil dele instantaneamente, clique no link abaixo:\n" +
                "" + urlAprovação + "\n\n" +
                "Atenciosamente,\n" +
                "Sistema de Eventos SEPLAG";
        message.setText(corpoEmail);

        try {
            // Tenta enviar o e-mail corporativo de verdade
            mailSender.send(message);
            System.out.println("\n📧 [E-MAIL] Notificação real enviada com sucesso para ryanmendes@seplag.mt.gov.br!");

        } catch (Exception e) {
            // Plano B: Evita quebra se o firewall da rede corporativa bloquear a porta em localhost
            System.out.println("\n⚠️ [AVISO DE REDE] O envio do e-mail real falhou devido ao firewall.");
            System.out.println("⚡ [PLANO B] Use o link do terminal para testar seu fluxo:");
            System.out.println("👉 CLIQUE AQUI PARA APROVAR O USUÁRIO:");
            System.out.println(urlAprovação);
            System.out.println("---------------------------------------------------\n");
        }
    }
}