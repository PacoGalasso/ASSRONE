package ASSRONE.backend.security;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokenGeneratorTest {

    private static final Pattern URL_SAFE_NO_PADDING = Pattern.compile("^[A-Za-z0-9_-]+$");

    @RepeatedTest(20)
    void genereUnTokenUrlSafeSansPadding() {
        String token = SecureTokenGenerator.generate();

        assertThat(token).doesNotContain("=").doesNotContain("+").doesNotContain("/");
        assertThat(URL_SAFE_NO_PADDING.matcher(token).matches()).isTrue();
    }

    @Test
    void genereDesTokensDistinctsAChaqueAppel() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(SecureTokenGenerator.generate());
        }

        assertThat(tokens).hasSize(1000);
    }

    @Test
    void genereUnTokenAvecAuMoinsQuaranteDeuxCaracteres() {
        // 32 octets encodés en base64 URL-safe sans padding produisent 43
        // caractères — vérifie que l'entropie annoncée (256 bits) est bien celle
        // réellement produite, pas une valeur tronquée.
        assertThat(SecureTokenGenerator.generate()).hasSize(43);
    }
}
