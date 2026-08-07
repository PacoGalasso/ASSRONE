package ASSRONE.backend.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256, hex-encoded — the one-way hash used for every opaque token this
 * application stores (password-reset, email-verification): the raw token is
 * emailed to the user and never persisted; only this hash is, so a database
 * read alone can never yield a usable token. Mirrors RefreshTokenService's
 * own token-hashing exactly, factored out here since it is now shared by two
 * more services rather than duplicated a third time.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponible dans cet environnement.", ex);
        }
    }
}
