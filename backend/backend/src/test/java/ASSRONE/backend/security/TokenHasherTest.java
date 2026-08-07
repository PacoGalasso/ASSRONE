package ASSRONE.backend.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void produitUnHashHexadecimalDeSoixanteQuatreCaracteresPourSha256() {
        String hash = TokenHasher.sha256Hex("un-token-quelconque");

        assertThat(hash).hasSize(64).matches("^[0-9a-f]{64}$");
    }

    @Test
    void estDeterministeriquePourLeMemeToken() {
        assertThat(TokenHasher.sha256Hex("meme-token")).isEqualTo(TokenHasher.sha256Hex("meme-token"));
    }

    @Test
    void produitDesHashsDifferentsPourDesTokensDifferents() {
        assertThat(TokenHasher.sha256Hex("token-a")).isNotEqualTo(TokenHasher.sha256Hex("token-b"));
    }

    @Test
    void neContientJamaisLeTokenEnClair() {
        String token = "token-secret-a-ne-jamais-retrouver-en-clair";
        String hash = TokenHasher.sha256Hex(token);

        assertThat(hash).doesNotContain(token);
    }
}
