package ASSRONE.backend.service;

import ASSRONE.backend.dto.EventRegistrationRequest;
import ASSRONE.backend.model.Event;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.contact.recipient}")
    private String recipient;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public void sendRegistrationConfirmation(Event event, EventRegistrationRequest registration) {
        SimpleMailMessage toParticipant = new SimpleMailMessage();
        toParticipant.setFrom(fromAddress);
        toParticipant.setTo(registration.getEmail());
        toParticipant.setSubject("Confirmation d'inscription — " + event.getTitle());
        toParticipant.setText(
                "Bonjour " + registration.getFullName() + ",\n\n"
                        + "Votre inscription à l'événement \"" + event.getTitle() + "\" est confirmée.\n\n"
                        + "Date : " + event.getEventDate() + "\n"
                        + "Horaire : " + event.getStartTime() + " - " + event.getEndTime() + "\n"
                        + "Lieu : " + event.getLocation() + "\n\n"
                        + "À bientôt,\nL'équipe ASSRONE"
        );
        mailSender.send(toParticipant);

        SimpleMailMessage toAssociation = new SimpleMailMessage();
        toAssociation.setFrom(fromAddress);
        toAssociation.setTo(recipient);
        toAssociation.setReplyTo(registration.getEmail());
        toAssociation.setSubject("[Inscription événement] " + event.getTitle());
        toAssociation.setText(
                registration.getFullName() + " (" + registration.getEmail() + ") vient de s'inscrire à \""
                        + event.getTitle() + "\" (" + event.getEventDate() + ").\n\n"
                        + "Participants : " + event.getCurrentParticipants() + " / " + event.getMaxParticipants()
        );
        mailSender.send(toAssociation);
    }
}
