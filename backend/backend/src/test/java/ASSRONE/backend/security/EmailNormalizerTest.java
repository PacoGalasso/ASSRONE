package ASSRONE.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNormalizerTest {

    @Test
    void supprimeLesEspacesEtMetEnMinuscule() {
        assertThat(EmailNormalizer.normalize("  Membre@ASSRONE.ch  ")).isEqualTo("membre@assrone.ch");
    }

    @Test
    void estIdempotentSurUneAdresseDejaNormalisee() {
        assertThat(EmailNormalizer.normalize("membre@assrone.ch")).isEqualTo("membre@assrone.ch");
    }

    @Test
    void retourneNullPourUneEntreeNull() {
        assertThat(EmailNormalizer.normalize(null)).isNull();
    }
}
