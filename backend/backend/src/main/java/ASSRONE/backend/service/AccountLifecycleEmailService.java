package ASSRONE.backend.service;

import ASSRONE.backend.config.AccountLifecycleProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Sends the two account-lifecycle emails (password reset, email
 * verification). Plain text via SimpleMailMessage, mirroring
 * EventEmailService exactly — this project has no HTML-mail infrastructure
 * (no MimeMessageHelper/template engine anywhere), and introducing one for
 * two short, simple messages isn't justified. Every link is built from
 * AccountLifecycleProperties#frontendUrl, never a hardcoded host.
 *
 * <p>The paths below are deliberately NOT prefixed with /auth: that prefix is
 * reserved for the backend API and is proxied straight to Spring Boot both in
 * dev (proxy.conf.json) and in production (nginx "location /auth/"), so a
 * link under it never reaches Angular's router — see app.routes.ts for the
 * matching frontend routes and the full incident this fixed.
 */
@Service
@RequiredArgsConstructor
public class AccountLifecycleEmailService {

    private final JavaMailSender mailSender;
    private final AccountLifecycleProperties properties;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        String url = buildUrl("/reset-password", rawToken);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Réinitialisation de votre mot de passe — ASSRONE");
        message.setText(
                "Bonjour,\n\n"
                        + "Une demande de réinitialisation de mot de passe a été effectuée pour ce compte.\n\n"
                        + "Pour choisir un nouveau mot de passe, cliquez sur le lien suivant (valable "
                        + formatDuration(properties.resetToken().expiry()) + ") :\n"
                        + url + "\n\n"
                        + "Si vous n'êtes pas à l'origine de cette demande, vous pouvez ignorer cet email : "
                        + "votre mot de passe actuel reste inchangé.\n\n"
                        + "L'équipe ASSRONE"
        );
        mailSender.send(message);
    }

    public void sendVerificationEmail(String toEmail, String rawToken) {
        String url = buildUrl("/verify-email", rawToken);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Vérifiez votre adresse email — ASSRONE");
        message.setText(
                "Bonjour,\n\n"
                        + "Merci de confirmer votre adresse email pour activer votre compte ASSRONE.\n\n"
                        + "Cliquez sur le lien suivant (valable "
                        + formatDuration(properties.verificationToken().expiry()) + ") :\n"
                        + url + "\n\n"
                        + "Si vous n'êtes pas à l'origine de cette inscription, vous pouvez ignorer cet email.\n\n"
                        + "L'équipe ASSRONE"
        );
        mailSender.send(message);
    }

    private String buildUrl(String path, String rawToken) {
        String encodedToken = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        return properties.frontendUrl() + path + "?token=" + encodedToken;
    }

    private static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        if (hours >= 1) {
            return hours + (hours == 1 ? " heure" : " heures");
        }
        long minutes = duration.toMinutes();
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
