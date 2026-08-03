package ASSRONE.backend.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EventRegistrationRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void fullNameDePlusDe150CaracteresEstRejete() {
        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("A".repeat(151))
                .email("jean.dupont@assrone.ch")
                .build();

        Set<ConstraintViolation<EventRegistrationRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
    }

    @Test
    void emailDePlusDe254CaracteresEstRejete() {
        String emailTropLong = "a".repeat(246) + "@assrone.ch";
        assertThat(emailTropLong.length()).isGreaterThan(254);

        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email(emailTropLong)
                .build();

        Set<ConstraintViolation<EventRegistrationRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    void requeteValideNeLeveAucuneViolation() {
        EventRegistrationRequest request = EventRegistrationRequest.builder()
                .fullName("Jean Dupont")
                .email("jean.dupont@assrone.ch")
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }
}
