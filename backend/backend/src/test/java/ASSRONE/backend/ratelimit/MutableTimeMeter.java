package ASSRONE.backend.ratelimit;

import io.github.bucket4j.TimeMeter;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manually advanceable clock so bucket-refill tests never need Thread.sleep().
 */
final class MutableTimeMeter implements TimeMeter {

    private final AtomicLong currentNanos = new AtomicLong();

    @Override
    public long currentTimeNanos() {
        return currentNanos.get();
    }

    @Override
    public boolean isWallClockBased() {
        return false;
    }

    void advance(Duration duration) {
        currentNanos.addAndGet(duration.toNanos());
    }
}
