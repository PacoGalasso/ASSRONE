package ASSRONE.backend.service;

import ASSRONE.backend.model.MembershipApplication;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MembershipEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.contact.recipient}")
    private String recipient;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public void sendApplicationNotification(MembershipApplication application) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(fromAddress);
        email.setTo(recipient);
        email.setReplyTo(application.getEmail());
        email.setSubject("[Adhésion ASSRONE] Nouvelle demande de " + application.getFullName());
        email.setText(
                "Nom : " + application.getFullName() + "\n"
                        + "Email : " + application.getEmail() + "\n"
                        + "Téléphone : " + (application.getPhone() != null ? application.getPhone() : "-") + "\n"
                        + "Type de membre : " + application.getMembershipType() + "\n\n"
                        + "Message :\n" + (application.getMessage() != null ? application.getMessage() : "-")
        );
        mailSender.send(email);
    }
}
