package ASSRONE.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manually advanceable clock so lockout-timing tests never need Thread.sleep().
 */
final class MutableClock extends Clock {

    private final ZoneId zone;
    private final AtomicReference<Instant> instant;

    MutableClock(Instant initialInstant, ZoneId zone) {
        this.zone = zone;
        this.instant = new AtomicReference<>(initialInstant);
    }

    void advance(Duration duration) {
        instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant.get(), zone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
