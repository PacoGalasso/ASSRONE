package ASSRONE.backend.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookiePropertiesTest {

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
    void unNomVideEstRejete() {
        RefreshCookieProperties properties = new RefreshCookieProperties("   ", true, "Lax", "/auth");

        Set<ConstraintViolation<RefreshCookieProperties>> violations = validator.validate(properties);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name"));
    }

    @Test
    void unCheminVideEstRejete() {
        RefreshCookieProperties properties = new RefreshCookieProperties("refresh_token", true, "Lax", "");

        Set<ConstraintViolation<RefreshCookieProperties>> violations = validator.validate(properties);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("path"));
    }

    @Test
    void unSameSiteInconnuEstRejete() {
        RefreshCookieProperties properties = new RefreshCookieProperties("refresh_token", true, "Loose", "/auth");

        Set<ConstraintViolation<RefreshCookieProperties>> violations = validator.validate(properties);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("sameSite"));
    }

    @Test
    void chacunDesTroisSameSiteReconnusEstAccepte() {
        for (String sameSite : new String[]{"Strict", "Lax", "None"}) {
            RefreshCookieProperties properties = new RefreshCookieProperties("refresh_token", true, sameSite, "/auth");

            assertThat(validator.validate(properties)).isEmpty();
        }
    }

    @Test
    void uneConfigurationValideNeLeveAucuneViolation() {
        RefreshCookieProperties properties = new RefreshCookieProperties("refresh_token", false, "Lax", "/auth");

        assertThat(validator.validate(properties)).isEmpty();
    }
}
