package ASSRONE.backend.ratelimit;

import java.time.Duration;

public enum RateLimitCategory {
    LOGIN(10, Duration.ofMinutes(1)),
    ACCOUNT_CREATION(5, Duration.ofHours(1)),
    CONTACT(5, Duration.ofHours(1)),
    MEMBERSHIP_APPLICATION(5, Duration.ofHours(1)),
    EVENT_REGISTRATION(20, Duration.ofHours(1));

    private final int capacity;
    private final Duration window;

    RateLimitCategory(int capacity, Duration window) {
        this.capacity = capacity;
        this.window = window;
    }

    public int getCapacity() {
        return capacity;
    }

    public Duration getWindow() {
        return window;
    }
}
