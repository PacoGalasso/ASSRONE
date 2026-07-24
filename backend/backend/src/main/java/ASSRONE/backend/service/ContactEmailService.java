package ASSRONE.backend.service;

import ASSRONE.backend.dto.ContactMessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.contact.recipient}")
    private String recipient;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public void sendContactMessage(ContactMessageRequest request) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(fromAddress);
        email.setTo(recipient);
        email.setReplyTo(request.getEmail());
        email.setSubject("[Contact ASSRONE] " + request.getSubject());
        email.setText(
                "De : " + request.getFullName() + " (" + request.getEmail() + ")\n\n" + request.getMessage()
        );
        mailSender.send(email);
    }
}