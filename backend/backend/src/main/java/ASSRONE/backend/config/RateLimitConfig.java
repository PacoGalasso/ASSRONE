package ASSRONE.backend.config;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.bucket4j.TimeMeter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    public TimeMeter timeMeter() {
        return TimeMeter.SYSTEM_NANOTIME;
    }

    @Bean
    public Ticker cacheTicker() {
        return Ticker.systemTicker();
    }
}
