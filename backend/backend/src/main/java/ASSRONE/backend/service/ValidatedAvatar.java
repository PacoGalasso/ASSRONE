package ASSRONE.backend.service;

import java.util.Arrays;

public record ValidatedAvatar(AvatarImageFormat format, byte[] content) {

    public ValidatedAvatar {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ValidatedAvatar that)) {
            return false;
        }
        return format == that.format && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        int result = format.hashCode();
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }
}
