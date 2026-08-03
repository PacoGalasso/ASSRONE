package ASSRONE.backend.service;

import ASSRONE.backend.event.EventRegistrationConfirmedEvent;
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

    public void sendRegistrationConfirmation(EventRegistrationConfirmedEvent event) {
        SimpleMailMessage toParticipant = new SimpleMailMessage();
        toParticipant.setFrom(fromAddress);
        toParticipant.setTo(event.participantEmail());
        toParticipant.setSubject("Confirmation d'inscription — " + event.eventTitle());
        toParticipant.setText(
                "Bonjour " + event.participantFullName() + ",\n\n"
                        + "Votre inscription à l'événement \"" + event.eventTitle() + "\" est confirmée.\n\n"
                        + "Date : " + event.eventDate() + "\n"
                        + "Horaire : " + event.startTime() + " - " + event.endTime() + "\n"
                        + "Lieu : " + event.location() + "\n\n"
                        + "À bientôt,\nL'équipe ASSRONE"
        );
        mailSender.send(toParticipant);

        SimpleMailMessage toAssociation = new SimpleMailMessage();
        toAssociation.setFrom(fromAddress);
        toAssociation.setTo(recipient);
        toAssociation.setReplyTo(event.participantEmail());
        toAssociation.setSubject("[Inscription événement] " + event.eventTitle());
        toAssociation.setText(
                event.participantFullName() + " (" + event.participantEmail() + ") vient de s'inscrire à \""
                        + event.eventTitle() + "\" (" + event.eventDate() + ").\n\n"
                        + "Participants : " + event.currentParticipants() + " / " + event.maxParticipants()
        );
        mailSender.send(toAssociation);
    }
}
