package ASSRONE.backend.exception;

/**
 * A session public ID that is syntactically valid but doesn't resolve to a
 * session owned by the caller — whether because it never existed, belongs
 * to another user, or was already purged. Deliberately identical whether
 * the ID is unknown or belongs to someone else: an owner-scoped 404 that
 * doesn't distinguish the two never reveals another user's session exists.
 */
public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String message) {
        super(message);
    }
}
