package ASSRONE.backend.service;

import java.util.Optional;

public enum AvatarImageFormat {
    JPEG(".jpg", "image/jpeg", "jpg"),
    PNG(".png", "image/png", "png");

    private final String extension;
    private final String mimeType;
    private final String writerFormatName;

    AvatarImageFormat(String extension, String mimeType, String writerFormatName) {
        this.extension = extension;
        this.mimeType = mimeType;
        this.writerFormatName = writerFormatName;
    }

    public String extension() {
        return extension;
    }

    public String mimeType() {
        return mimeType;
    }

    public String writerFormatName() {
        return writerFormatName;
    }

    public static Optional<AvatarImageFormat> fromReaderFormatName(String readerFormatName) {
        if (readerFormatName == null) {
            return Optional.empty();
        }
        String normalized = readerFormatName.trim().toUpperCase();
        if (normalized.equals("JPEG") || normalized.equals("JPG")) {
            return Optional.of(JPEG);
        }
        if (normalized.equals("PNG")) {
            return Optional.of(PNG);
        }
        return Optional.empty();
    }
}
