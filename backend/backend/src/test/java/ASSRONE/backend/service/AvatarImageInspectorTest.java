package ASSRONE.backend.service;

import ASSRONE.backend.exception.InvalidAvatarException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarImageInspectorTest {

    private final AvatarImageInspector inspector = new AvatarImageInspector();

    private static byte[] fixture(String name) throws IOException {
        try (InputStream stream = AvatarImageInspectorTest.class.getResourceAsStream("/avatars/" + name)) {
            if (stream == null) {
                throw new IllegalStateException("Fixture manquante : " + name);
            }
            return stream.readAllBytes();
        }
    }

    @Test
    void unVraiJpegEstAccepte() throws IOException {
        ValidatedAvatar result = inspector.inspect(fixture("small-valid.jpg"));

        assertThat(result.format()).isEqualTo(AvatarImageFormat.JPEG);
    }

    @Test
    void unVraiPngEstAccepte() throws IOException {
        ValidatedAvatar result = inspector.inspect(fixture("small-valid.png"));

        assertThat(result.format()).isEqualTo(AvatarImageFormat.PNG);
    }

    @Test
    void leFormatDetermineLExtensionCanoniqueEtLeMimeValide() throws IOException {
        ValidatedAvatar result = inspector.inspect(fixture("small-valid.png"));

        assertThat(result.format().extension()).isEqualTo(".png");
        assertThat(result.format().mimeType()).isEqualTo("image/png");
    }

    @Test
    void leContenuReencodeNEstJamaisVide() throws IOException {
        ValidatedAvatar result = inspector.inspect(fixture("small-valid.jpg"));

        assertThat(result.content()).isNotEmpty();
    }

    @Test
    void leContenuReencodeResteDecodable() throws IOException {
        ValidatedAvatar result = inspector.inspect(fixture("small-valid.png"));

        BufferedImage reencoded = ImageIO.read(new ByteArrayInputStream(result.content()));

        assertThat(reencoded).isNotNull();
    }

    @Test
    void unTexteRenommeEnJpgEstRejete() throws IOException {
        byte[] content = fixture("not-an-image.jpg");

        assertThatThrownBy(() -> inspector.inspect(content))
                .isInstanceOf(InvalidAvatarException.class);
    }

    @Test
    void unFichierVideEstRejete() throws IOException {
        byte[] content = fixture("empty.jpg");

        assertThatThrownBy(() -> inspector.inspect(content))
                .isInstanceOf(InvalidAvatarException.class);
    }

    @Test
    void unTableauNulEstRejete() {
        assertThatThrownBy(() -> inspector.inspect(null))
                .isInstanceOf(InvalidAvatarException.class);
    }

    @Test
    void unVraiGifEstRejete() throws IOException {
        byte[] content = fixture("small-valid.gif");

        assertThatThrownBy(() -> inspector.inspect(content))
                .isInstanceOf(InvalidAvatarException.class);
    }

    @Test
    void uneImageAuxDimensionsExcessivesEstRejeteeAvantDecodageComplet() throws IOException {
        byte[] content = fixture("oversized-header.png");

        assertThatThrownBy(() -> inspector.inspect(content))
                .isInstanceOf(InvalidAvatarException.class);
    }

    @Test
    void leTableauDeContenuRetourneEstProtegeContreLesModificationsExternes() throws IOException {
        ValidatedAvatar result = inspector.inspect(fixture("small-valid.jpg"));

        byte[] exposed = result.content();
        exposed[0] = (byte) ~exposed[0];

        assertThat(result.content()).isNotEqualTo(exposed);
    }

    @Test
    void laDetectionDuFormatIgnoreLaCasse() {
        assertThat(AvatarImageFormat.fromReaderFormatName("png")).contains(AvatarImageFormat.PNG);
        assertThat(AvatarImageFormat.fromReaderFormatName("PNG")).contains(AvatarImageFormat.PNG);
        assertThat(AvatarImageFormat.fromReaderFormatName("JPEG")).contains(AvatarImageFormat.JPEG);
        assertThat(AvatarImageFormat.fromReaderFormatName("jpg")).contains(AvatarImageFormat.JPEG);
        assertThat(AvatarImageFormat.fromReaderFormatName("bmp")).isEmpty();
    }
}
