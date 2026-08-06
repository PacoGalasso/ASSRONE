package ASSRONE.backend.audit;

/**
 * The closed set of security-relevant events this application records.
 * Deliberately not exhaustive of every possible action — routine reads
 * (public GETs, listing pages) are never events here; only actions with
 * security or accountability significance are. Adding a new event means
 * adding a new constant here, never a free-text description at the call
 * site — that stability is what makes the log line format parseable by
 * something other than a human, now or in the future.
 */
public enum SecurityEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    ACCOUNT_LOCKED,
    REFRESH_SUCCESS,
    REFRESH_DENIED,
    REFRESH_TOKEN_REUSE_DETECTED,
    LOGOUT,

    USER_CREATED,
    USER_DELETED,
    ROLE_CHANGE,
    ADMIN_ACTION_DENIED,

    MEMBERSHIP_ACCEPTED,
    MEMBERSHIP_REJECTED,

    EVENT_CREATED,
    EVENT_UPDATED,
    EVENT_DELETED,

    DOCUMENT_UPLOADED,
    DOCUMENT_DELETED,

    COMMITTEE_MEMBER_CREATED,
    COMMITTEE_MEMBER_UPDATED,
    COMMITTEE_MEMBER_DELETED,
    COMMITTEE_MEMBER_PHOTO_CHANGED,

    RATE_LIMIT_EXCEEDED,
    ORIGIN_REJECTED,

    SESSION_REVOKED,
    OTHER_SESSIONS_REVOKED,
    ALL_SESSIONS_REVOKED,
    SESSION_LIMIT_ENFORCED,
    SESSION_CLEANUP_COMPLETED,
    SESSION_REVOCATION_DENIED
}
