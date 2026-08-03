package ASSRONE.backend.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.TimeMeter;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * In-memory, per-instance token buckets keyed by "<ip>|<category>". Buckets are
 * created lazily and evicted after an hour of inactivity (Caffeine) so the map
 * stays bounded without a manual cleanup thread. Restarting the application
 * resets all counters — accepted for this lot (see LOT 7 audit).
 */
@Service
public class RateLimiterService {

    private static final Duration BUCKET_EXPIRY = Duration.ofHours(1);
    private static final long MAX_TRACKED_BUCKETS = 100_000;

    private final TimeMeter timeMeter;
    private final Cache<String, Bucket> buckets;

    public RateLimiterService(TimeMeter timeMeter, Ticker cacheTicker) {
        this.timeMeter = timeMeter;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(BUCKET_EXPIRY)
                .maximumSize(MAX_TRACKED_BUCKETS)
                .ticker(cacheTicker)
                .build();
    }

    public ConsumptionProbe tryConsume(String clientIp, RateLimitCategory category) {
        String key = clientIp + "|" + category.name();
        Bucket bucket = buckets.get(key, k -> newBucket(category));
        return bucket.tryConsumeAndReturnRemaining(1);
    }

    long estimatedBucketCount() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }

    private Bucket newBucket(RateLimitCategory category) {
        Bandwidth limit = Bandwidth.classic(
                category.getCapacity(),
                Refill.greedy(category.getCapacity(), category.getWindow()));
        return Bucket.builder()
                .withCustomTimePrecision(timeMeter)
                .addLimit(limit)
                .build();
    }
}
