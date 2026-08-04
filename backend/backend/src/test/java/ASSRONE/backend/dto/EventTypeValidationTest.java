package ASSRONE.backend.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EventTypeValidationTest {

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

    private static CreateEventRequest createRequest(String type) {
        return CreateEventRequest.builder()
                .title("Atelier bénévolat")
                .description("Description")
                .type(type)
                .eventDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 0))
                .location("Local associatif")
                .maxParticipants(10)
                .build();
    }

    private static UpdateEventRequest updateRequest(String type) {
        return UpdateEventRequest.builder()
                .title("Atelier bénévolat")
                .description("Description")
                .type(type)
                .eventDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(20, 0))
                .location("Local associatif")
                .maxParticipants(10)
                .build();
    }

    @Test
    void typeLibreValideNeLeveAucuneViolationSurCreation() {
        assertThat(validator.validate(createRequest("Rencontre"))).isEmpty();
    }

    @Test
    void typeLibreValideNeLeveAucuneViolationSurModification() {
        assertThat(validator.validate(updateRequest("Rencontre"))).isEmpty();
    }

    @Test
    void typeAvecAccentsEstAccepte() {
        assertThat(validator.validate(createRequest("Conférence académique"))).isEmpty();
    }

    @Test
    void typeNullEstRefuseSurCreation() {
        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(createRequest(null));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("type"));
    }

    @Test
    void typeVideEstRefuse() {
        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(createRequest(""));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("type"));
    }

    @Test
    void typeUniquementDesEspacesEstRefuse() {
        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(createRequest("    "));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("type"));
    }

    @Test
    void typeDUnSeulCaractereEstRefuse() {
        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(createRequest("A"));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("type"));
    }

    @Test
    void typeDeExactement80CaracteresEstAccepte() {
        String type = "A".repeat(80);
        assertThat(validator.validate(createRequest(type))).isEmpty();
    }

    @Test
    void typeDePlusDe80CaracteresEstRefuse() {
        String tropLong = "A".repeat(81);
        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(createRequest(tropLong));
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("type"));
    }

    @Test
    void anciennesValeursHistoriquesRestentValides() {
        assertThat(validator.validate(updateRequest("WEBINAIRE"))).isEmpty();
        assertThat(validator.validate(updateRequest("CONFÉRENCE"))).isEmpty();
        assertThat(validator.validate(updateRequest("ATELIER"))).isEmpty();
    }
}
