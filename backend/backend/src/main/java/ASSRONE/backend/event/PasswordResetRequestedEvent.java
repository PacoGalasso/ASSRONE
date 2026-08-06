package ASSRONE.backend.event;

/**
 * Published only when a real account was found for the requested email —
 * see PasswordResetService#requestReset for why a non-existent email never
 * reaches this point. rawToken exists only for the lifetime of this
 * in-memory event, read once by AccountLifecycleEmailListener to build the
 * reset URL and never logged; nothing here is ever persisted.
 */
public record PasswordResetRequestedEvent(String email, String rawToken) {
}
