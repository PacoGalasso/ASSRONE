package ASSRONE.backend.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTest {

    private final MutableTimeMeter timeMeter = new MutableTimeMeter();
    private final MutableTicker ticker = new MutableTicker();
    private final RateLimiterService service = new RateLimiterService(timeMeter, ticker);

    @Test
    void requetesSousLaLimiteSontToutesConsommees() {
        for (int i = 0; i < RateLimitCategory.LOGIN.getCapacity() - 1; i++) {
            assertThat(service.tryConsume("10.0.0.1", RateLimitCategory.LOGIN).isConsumed()).isTrue();
        }
    }

    @Test
    void premiereRequeteDepassantLaLimiteEstRejetee() {
        for (int i = 0; i < RateLimitCategory.LOGIN.getCapacity(); i++) {
            assertThat(service.tryConsume("10.0.0.2", RateLimitCategory.LOGIN).isConsumed()).isTrue();
        }

        ConsumptionProbe rejected = service.tryConsume("10.0.0.2", RateLimitCategory.LOGIN);
        assertThat(rejected.isConsumed()).isFalse();
    }

    @Test
    void retryAfterEstPresentEtPositifQuandRejete() {
        for (int i = 0; i < RateLimitCategory.LOGIN.getCapacity(); i++) {
            service.tryConsume("10.0.0.3", RateLimitCategory.LOGIN);
        }

        ConsumptionProbe rejected = service.tryConsume("10.0.0.3", RateLimitCategory.LOGIN);
        assertThat(rejected.isConsumed()).isFalse();
        assertThat(rejected.getNanosToWaitForRefill()).isPositive();
    }

    @Test
    void deuxIpDifferentesOntDesBucketsIndependants() {
        for (int i = 0; i < RateLimitCategory.LOGIN.getCapacity(); i++) {
            assertThat(service.tryConsume("10.0.0.4", RateLimitCategory.LOGIN).isConsumed()).isTrue();
        }
        assertThat(service.tryConsume("10.0.0.4", RateLimitCategory.LOGIN).isConsumed()).isFalse();

        assertThat(service.tryConsume("10.0.0.5", RateLimitCategory.LOGIN).isConsumed()).isTrue();
    }

    @Test
    void deuxCategoriesDifferentesSontIndependantesPourLaMemeIp() {
        for (int i = 0; i < RateLimitCategory.LOGIN.getCapacity(); i++) {
            service.tryConsume("10.0.0.6", RateLimitCategory.LOGIN);
        }
        assertThat(service.tryConsume("10.0.0.6", RateLimitCategory.LOGIN).isConsumed()).isFalse();

        assertThat(service.tryConsume("10.0.0.6", RateLimitCategory.CONTACT).isConsumed()).isTrue();
    }

    @Test
    void resetApresRefillDeLaFenetre() {
        for (int i = 0; i < RateLimitCategory.LOGIN.getCapacity(); i++) {
            service.tryConsume("10.0.0.7", RateLimitCategory.LOGIN);
        }
        assertThat(service.tryConsume("10.0.0.7", RateLimitCategory.LOGIN).isConsumed()).isFalse();

        timeMeter.advance(RateLimitCategory.LOGIN.getWindow());

        assertThat(service.tryConsume("10.0.0.7", RateLimitCategory.LOGIN).isConsumed()).isTrue();
    }

    @Test
    void bucketsExpiresSontEvincesDuCache() {
        service.tryConsume("10.0.0.8", RateLimitCategory.LOGIN);
        assertThat(service.estimatedBucketCount()).isEqualTo(1);

        ticker.advance(Duration.ofHours(2));

        assertThat(service.estimatedBucketCount()).isZero();
    }

    @Test
    void concurrenceSurUnMemeBucketNeDepassePasLaCapaciteAutorisee() throws Exception {
        int attempts = 50;
        int capacity = RateLimitCategory.CONTACT.getCapacity();
        CountDownLatch latch = new CountDownLatch(attempts);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        AtomicInteger successCount = new AtomicInteger();
        try {
            List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, attempts)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        latch.countDown();
                        latch.await();
                        if (service.tryConsume("10.0.0.9", RateLimitCategory.CONTACT).isConsumed()) {
                            successCount.incrementAndGet();
                        }
                        return null;
                    })
                    .toList();

            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(successCount.get()).isEqualTo(capacity);
    }
}
