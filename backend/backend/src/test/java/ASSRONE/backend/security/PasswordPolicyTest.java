package ASSRONE.backend.security;

import ASSRONE.backend.exception.InvalidPasswordException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @Test
    void refuseUnMotDePasseNull() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void refuseUneChaineVideOuUniquementDesEspaces() {
        assertThatThrownBy(() -> PasswordPolicy.validate("   "))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void refuseUnMotDePasseTropCourt() {
        assertThatThrownBy(() -> PasswordPolicy.validate("court12"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void accepteUnMotDePasseDeHuitCaracteres() {
        assertThatCode(() -> PasswordPolicy.validate("12345678")).doesNotThrowAnyException();
    }

    @Test
    void accepteUnMotDePasseDeSoixanteDouzeOctetsExactement() {
        String motDePasse = "a".repeat(72);
        assertThatCode(() -> PasswordPolicy.validate(motDePasse)).doesNotThrowAnyException();
    }

    @Test
    void refuseUnMotDePasseDeSoixanteTreizeOctets() {
        String motDePasse = "a".repeat(73);
        assertThatThrownBy(() -> PasswordPolicy.validate(motDePasse))
                .isInstanceOf(InvalidPasswordException.class);
    }

    // Caractère multi-octet (é = 2 octets en UTF-8) : la limite est vérifiée en
    // octets, pas en caractères, pour rester cohérente avec la troncature
    // silencieuse de BCrypt à 72 octets.
    @Test
    void refuseUnMotDePasseDeMoinsDeSoixanteTreizeCaracteresMaisPlusDeSoixanteTreizeOctets() {
        String motDePasse = "é".repeat(37);
        assertThatThrownBy(() -> PasswordPolicy.validate(motDePasse))
                .isInstanceOf(InvalidPasswordException.class);
    }
}
