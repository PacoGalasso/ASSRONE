package ASSRONE.backend.service;

import ASSRONE.backend.exception.InvalidAvatarException;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

@Service
public class AvatarImageInspector {

    private static final int MAX_DIMENSION_PX = 4096;
    private static final long MAX_TOTAL_PIXELS = 16_777_216L;
    private static final float JPEG_QUALITY = 0.90f;

    public ValidatedAvatar inspect(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidAvatarException("Le fichier est vide.");
        }

        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (iis == null) {
                throw new InvalidAvatarException("Format d'image non reconnu.");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new InvalidAvatarException("Format d'image non reconnu.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);

                AvatarImageFormat format = AvatarImageFormat.fromReaderFormatName(reader.getFormatName())
                        .orElseThrow(() -> new InvalidAvatarException("Seuls les formats JPEG et PNG sont acceptés."));

                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new InvalidAvatarException("Dimensions d'image invalides.");
                }
                if (width > MAX_DIMENSION_PX || height > MAX_DIMENSION_PX) {
                    throw new InvalidAvatarException("Image trop grande.");
                }
                if (width * height > MAX_TOTAL_PIXELS) {
                    throw new InvalidAvatarException("Image trop grande.");
                }

                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new InvalidAvatarException("Le contenu de l'image est illisible.");
                }

                byte[] reencoded = reencode(decoded, format);
                return new ValidatedAvatar(format, reencoded);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidAvatarException("Le contenu de l'image est illisible.");
        }
    }

    private byte[] reencode(BufferedImage decoded, AvatarImageFormat format) throws IOException {
        return switch (format) {
            case JPEG -> reencodeJpeg(decoded);
            case PNG -> reencodePng(decoded);
        };
    }

    private byte[] reencodeJpeg(BufferedImage decoded) throws IOException {
        BufferedImage rgb = toRgbIfNeeded(decoded);

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(AvatarImageFormat.JPEG.writerFormatName());
        if (!writers.hasNext()) {
            throw new IllegalStateException("Aucun encodeur JPEG disponible dans cet environnement.");
        }

        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(output)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(rgb, null, null), param);
            }
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private byte[] reencodePng(BufferedImage decoded) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean written = ImageIO.write(decoded, AvatarImageFormat.PNG.writerFormatName(), output);
        if (!written) {
            throw new IllegalStateException("Aucun encodeur PNG disponible dans cet environnement.");
        }
        return output.toByteArray();
    }

    private static BufferedImage toRgbIfNeeded(BufferedImage source) {
        if (!source.getColorModel().hasAlpha()) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }
}
