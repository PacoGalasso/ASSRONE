package ASSRONE.backend.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPropertiesTest {

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
    void uneListeVideEstRejetee() {
        CorsProperties properties = new CorsProperties(List.of());

        Set<ConstraintViolation<CorsProperties>> violations = validator.validate(properties);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("allowedOrigins"));
    }

    @Test
    void unWildcardEstRejeteMemeParmiDesOriginesValides() {
        CorsProperties properties = new CorsProperties(List.of("https://assrone.ch", "*"));

        Set<ConstraintViolation<CorsProperties>> violations = validator.validate(properties);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("freeOfWildcard"));
    }

    @Test
    void uneOrigineVideDansLaListeEstRejetee() {
        CorsProperties properties = new CorsProperties(List.of("https://assrone.ch", "   "));

        Set<ConstraintViolation<CorsProperties>> violations = validator.validate(properties);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().contains("allowedOrigins"));
    }

    @Test
    void uneListeDOriginesExplicitesEstAcceptee() {
        CorsProperties properties = new CorsProperties(List.of("https://assrone.ch", "https://www.assrone.ch"));

        assertThat(validator.validate(properties)).isEmpty();
    }
}
