package ASSRONE.backend.audit;

/** The outcome of a {@link SecurityEventType} — every event carries exactly one. */
public enum SecurityEventResult {
    SUCCESS,
    DENIED,
    ERROR
}
