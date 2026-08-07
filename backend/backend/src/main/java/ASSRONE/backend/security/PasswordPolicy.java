package ASSRONE.backend.security;

import ASSRONE.backend.exception.InvalidPasswordException;

import java.nio.charset.StandardCharsets;

/**
 * The single password-strength policy this application enforces, shared by
 * every path that ever sets a password (profile change, reset). Deliberately
 * length-only: no mandatory symbol/uppercase/digit class, which pushes users
 * toward predictable substitutions (a trailing "1!") without meaningfully
 * raising entropy — a longer passphrase already resists brute force better.
 *
 * MAX_LENGTH_BYTES is not an arbitrary abuse guard: BCryptPasswordEncoder
 * (see PasswordConfig) silently truncates any input over 72 bytes and
 * happily encodes whatever's left, so a longer password would appear
 * accepted while actually only its first 72 bytes matter — exactly the
 * silent truncation this policy exists to prevent. Checked in UTF-8 bytes,
 * not characters, since a password containing multi-byte characters can
 * cross that limit well before 72 characters.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidPasswordException("Le mot de passe est obligatoire.");
        }
        if (password.length() < MIN_LENGTH) {
            throw new InvalidPasswordException(
                    "Le mot de passe doit contenir au moins " + MIN_LENGTH + " caractères.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_LENGTH_BYTES) {
            throw new InvalidPasswordException(
                    "Le mot de passe est trop long (maximum " + MAX_LENGTH_BYTES + " octets).");
        }
    }
}
