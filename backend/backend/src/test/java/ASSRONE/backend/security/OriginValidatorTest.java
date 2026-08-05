package ASSRONE.backend.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OriginValidatorTest {

    private final OriginValidator validator = new OriginValidator(
            List.of("https://assrone.ch", "http://localhost:4200"));

    @Test
    void origineAutoriseeExacteEstAcceptee() {
        assertThat(validator.isAllowed("https://assrone.ch", null)).isTrue();
    }

    @Test
    void origineEtrangereEstRejetee() {
        assertThat(validator.isAllowed("https://evil.com", null)).isFalse();
    }

    @Test
    void mauvaisSchemaEstRejete() {
        assertThat(validator.isAllowed("http://assrone.ch", null)).isFalse();
    }

    @Test
    void mauvaisPortEstRejete() {
        assertThat(validator.isAllowed("http://localhost:9999", null)).isFalse();
    }

    @Test
    void sousDomaineNonDeclareEstRejete() {
        assertThat(validator.isAllowed("https://evil.assrone.ch", null)).isFalse();
    }

    @Test
    void suffixeTrompeurNestPasConfonduAvecUneOrigineAutorisee() {
        // A naive startsWith/contains check would wrongly accept this.
        assertThat(validator.isAllowed("https://assrone.ch.evil.com", null)).isFalse();
    }

    @Test
    void origineNullLitteraleEstRejetee() {
        assertThat(validator.isAllowed("null", null)).isFalse();
    }

    @Test
    void origineMalformeeEstRejetee() {
        assertThat(validator.isAllowed("not a valid origin", null)).isFalse();
    }

    @Test
    void refererAutoriseSansOrigineEstAccepte() {
        assertThat(validator.isAllowed(null, "https://assrone.ch/some/page?x=1")).isTrue();
    }

    @Test
    void refererEtrangerSansOrigineEstRejete() {
        assertThat(validator.isAllowed(null, "https://evil.com/some/page")).isFalse();
    }

    @Test
    void absenceDOrigineEtDeRefererEstRejetee() {
        assertThat(validator.isAllowed(null, null)).isFalse();
    }

    @Test
    void origineFaitAutoriteMemeSiLeRefererEstEtranger() {
        assertThat(validator.isAllowed("https://assrone.ch", "https://evil.com/page")).isTrue();
    }

    @Test
    void portStandardImpliciteCorrespondAuPortExpliciteEquivalent() {
        assertThat(validator.isAllowed("https://assrone.ch:443", null)).isTrue();
    }

    @Test
    void comparaisonDeSchemaEtDHoteEstInsensibleALaCasse() {
        assertThat(validator.isAllowed("HTTPS://ASSRONE.CH", null)).isTrue();
    }
}
