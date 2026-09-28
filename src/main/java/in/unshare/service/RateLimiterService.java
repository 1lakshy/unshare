package in.unshare.service;

import in.unshare.config.AppProperties;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory fixed-window limiter, adequate for a single instance.
 * If the backend is ever scaled horizontally, replace with a shared store (e.g. Redis)
 * so limits are enforced across instances instead of per-process.
 */
@Service
public class RateLimiterService {

    private record Window(Instant windowStart, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AppProperties appProperties;
    private final Clock clock;

    public RateLimiterService(AppProperties appProperties, Clock clock) {
        this.appProperties = appProperties;
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        Instant now = clock.instant();
        int windowSeconds = appProperties.rateLimit().windowSeconds();
        int capacity = appProperties.rateLimit().capacity();

        Window updated = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart().plusSeconds(windowSeconds).isBefore(now)) {
                return new Window(now, 1);
            }
            return new Window(existing.windowStart(), existing.count() + 1);
        });

        return updated.count() <= capacity;
    }
}
