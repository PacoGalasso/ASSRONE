package ASSRONE.backend.security;

import java.util.Locale;

/**
 * The one normalization rule every account-lifecycle lookup must agree on
 * (trim + lowercase) — used wherever a new lookup path is added by this
 * account-lifecycle work, so it can't drift from the equivalent inline
 * normalization already established elsewhere (UserController#normalizeEmail,
 * UserInfoService#addUser, UserProfileService#updateProfile), which this
 * class deliberately leaves untouched rather than retrofitting.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
