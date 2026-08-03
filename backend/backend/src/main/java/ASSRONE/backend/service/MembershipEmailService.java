package ASSRONE.backend.service;

import ASSRONE.backend.event.MembershipApplicationSubmittedEvent;
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

    public void sendApplicationSubmittedEmails(MembershipApplicationSubmittedEvent event) {
        SimpleMailMessage toAssociation = new SimpleMailMessage();
        toAssociation.setFrom(fromAddress);
        toAssociation.setTo(recipient);
        toAssociation.setReplyTo(event.email());
        toAssociation.setSubject("[Adhésion ASSRONE] Nouvelle demande de " + event.fullName());
        toAssociation.setText(
                "Nom : " + event.fullName() + "\n"
                        + "Email : " + event.email() + "\n"
                        + "Téléphone : " + (event.phone() != null ? event.phone() : "-") + "\n"
                        + "Type de membre : " + event.membershipType() + "\n\n"
                        + "Message :\n" + (event.message() != null ? event.message() : "-")
        );
        mailSender.send(toAssociation);

        SimpleMailMessage toApplicant = new SimpleMailMessage();
        toApplicant.setFrom(fromAddress);
        toApplicant.setTo(event.email());
        toApplicant.setSubject("Votre demande d'adhésion à ASSRONE a bien été reçue");
        toApplicant.setText(
                "Bonjour " + event.fullName() + ",\n\n"
                        + "Nous avons bien reçu votre demande d'adhésion à ASSRONE. Le Comité va l'examiner et vous recontactera prochainement.\n\n"
                        + "À bientôt,\nL'équipe ASSRONE"
        );
        mailSender.send(toApplicant);
    }

    public void sendAccountCreated(MembershipApplication application, String rawPassword) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setFrom(fromAddress);
        email.setTo(application.getEmail());
        email.setSubject("Bienvenue à ASSRONE — vos identifiants de connexion");
        email.setText(
                "Bonjour " + application.getFullName() + ",\n\n"
                        + "Votre demande d'adhésion à ASSRONE a été acceptée. Voici vos identifiants de connexion :\n\n"
                        + "Email : " + application.getEmail() + "\n"
                        + "Mot de passe temporaire : " + rawPassword + "\n\n"
                        + "Vous pourrez modifier ce mot de passe depuis votre profil après connexion.\n\n"
                        + "Le paiement de la cotisation annuelle se fait sur facture, qui vous sera envoyée séparément.\n\n"
                        + "Bienvenue parmi nous,\nL'équipe ASSRONE"
        );
        mailSender.send(email);
    }
}
