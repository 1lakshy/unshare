package in.unshare.service;

import in.unshare.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTest {

    private AppProperties propertiesWith(int capacity, int windowSeconds) {
        return new AppProperties(
                "http://localhost:5173",
                new AppProperties.Share(30, 180, 50, 10_000L, 1, 5),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.RateLimit(capacity, windowSeconds));
    }

    @Test
    void allowsRequestsUpToCapacityWithinWindow() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        RateLimiterService limiter = new RateLimiterService(propertiesWith(3, 60), clock);

        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
    }

    @Test
    void tracksDifferentKeysIndependently() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        RateLimiterService limiter = new RateLimiterService(propertiesWith(1, 60), clock);

        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("5.6.7.8")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
    }
}
