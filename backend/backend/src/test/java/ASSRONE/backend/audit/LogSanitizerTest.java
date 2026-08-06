package ASSRONE.backend.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogSanitizerTest {

    @Test
    void nullDevientTiret() {
        assertThat(LogSanitizer.sanitize(null)).isEqualTo("-");
    }

    @Test
    void chaineVideDevientTiret() {
        assertThat(LogSanitizer.sanitize("")).isEqualTo("-");
    }

    @Test
    void chaineNormaleEstPreservee() {
        assertThat(LogSanitizer.sanitize("REFRESH_DENIED")).isEqualTo("REFRESH_DENIED");
    }

    @Test
    void sautDeLigneEstNeutralise() {
        String result = LogSanitizer.sanitize("valeur\nsecurity_event eventType=LOGIN_SUCCESS");
        assertThat(result).doesNotContain("\n");
    }

    @Test
    void retourChariotEstNeutralise() {
        String result = LogSanitizer.sanitize("valeur\rinjectee");
        assertThat(result).doesNotContain("\r");
    }

    @Test
    void tabulationEstNeutralisee() {
        String result = LogSanitizer.sanitize("valeur\tinjectee");
        assertThat(result).doesNotContain("\t");
    }

    @Test
    void caractereDeControleEstNeutralise() {
        String result = LogSanitizer.sanitize("valeurinjectee");
        assertThat(result).doesNotContain("");
    }

    @Test
    void uneValeurAvecUniquementDesCaracteresDeControleDevientTiret() {
        assertThat(LogSanitizer.sanitize("\n\r\t")).isEqualTo("-");
    }

    @Test
    void injectionDeSecondeLigneNePeutPasProduireDeNouvelleLigneDansLeResultat() {
        String tentativeInjection = "actorId-legitime\nsecurity_event eventType=ROLE_CHANGE result=SUCCESS actorId=attaquant";
        String result = LogSanitizer.sanitize(tentativeInjection);

        assertThat(result.lines().count()).isEqualTo(1);
    }

    @Test
    void valeurExcessivementLongueEstTronqueeAvecSuffixe() {
        String longue = "a".repeat(500);

        String result = LogSanitizer.sanitize(longue);

        assertThat(result).hasSize(200);
        assertThat(result).endsWith("...");
    }

    @Test
    void valeurDeLongueurLimiteNestPasTronquee() {
        String pileALaLimite = "a".repeat(200);

        String result = LogSanitizer.sanitize(pileALaLimite);

        assertThat(result).isEqualTo(pileALaLimite);
    }
}
