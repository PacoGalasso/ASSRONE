package ASSRONE.backend.security;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates the opaque, single-use tokens emailed to a user for password
 * reset and email verification. 32 random bytes (256 bits) from a CSPRNG,
 * URL-safe base64 without padding — long and unpredictable enough that
 * guessing one is infeasible, and safe to embed directly in a query string.
 * Deliberately not a JWT: these tokens carry no claims, are never presented
 * as a Bearer credential, and their entire lifecycle (single-use, expiry,
 * revocation-on-reuse) is enforced by the database row backing their hash —
 * exactly the properties an unsigned, unverifiable, un-revocable JWT would
 * not give us for free.
 */
public final class SecureTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokenGenerator() {
    }

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
