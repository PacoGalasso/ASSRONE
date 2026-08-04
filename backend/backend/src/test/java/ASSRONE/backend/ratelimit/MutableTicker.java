package ASSRONE.backend.ratelimit;

import com.github.benmanes.caffeine.cache.Ticker;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manually advanceable Caffeine ticker so cache-expiry tests never need Thread.sleep().
 */
final class MutableTicker implements Ticker {

    private final AtomicLong currentNanos = new AtomicLong();

    @Override
    public long read() {
        return currentNanos.get();
    }

    void advance(Duration duration) {
        currentNanos.addAndGet(duration.toNanos());
    }
}
