package csw.youtube.chat.common.config.ratelimit;


import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class GcraRateLimiter {

    final ConcurrentHashMap<String, AtomicLong> lastTheoreticalArrivalTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimiterConfig> rateLimiterConfigs = new ConcurrentHashMap<>();
    private final Object configLock = new Object(); // Lock for config updates to ensure thread safety during configuration

    public RateLimiterConfig getRateLimiterConfig(String key) {
        return rateLimiterConfigs.get(key); // Simple getter
    }

    public void configureRateLimiter(String key, double permitsPerSecond, Duration tolerance) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("Permits per second must be positive.");
        }
        if (tolerance.isNegative()) {
            throw new IllegalArgumentException("Tolerance must be non-negative.");
        }

        long emissionIntervalNanos = (long) (1_000_000_000.0 / permitsPerSecond);
        RateLimiterConfig newConfig = new RateLimiterConfig(emissionIntervalNanos, tolerance.toNanos(), permitsPerSecond);

        // Synchronize configuration updates to avoid race conditions if multiple threads try to configure the same key concurrently
        synchronized (configLock) {
            RateLimiterConfig existingConfig = rateLimiterConfigs.get(key);
            if (existingConfig == null || !existingConfig.equals(newConfig)) {
                rateLimiterConfigs.put(key, newConfig);
                lastTheoreticalArrivalTime.computeIfAbsent(key, k -> new AtomicLong(0L)); // Initialize TAT if new key or config changed
            }
        }
    }

    public boolean allowRequest(String key) {
        RateLimiterConfig config = rateLimiterConfigs.get(key);
        if (config == null) {
            throw new IllegalStateException("Rate limiter not configured for key: " + key);
        }

        long now = System.nanoTime();
        AtomicLong tatRef = lastTheoreticalArrivalTime.get(key);
        long currentTAT = tatRef.get();
        long earliestTime = currentTAT - config.toleranceNanos;

        if (now > earliestTime) {
            long nextTAT;
            if (now > currentTAT) {
                nextTAT = now + config.emissionIntervalNanos;
            } else {
                nextTAT = currentTAT + config.emissionIntervalNanos;
            }

            while (!tatRef.compareAndSet(currentTAT, nextTAT)) {
                currentTAT = tatRef.get();
                earliestTime = currentTAT - config.toleranceNanos;
                if (now <= earliestTime) {
                    return false;
                }
                if (now > currentTAT) {
                    nextTAT = now + config.emissionIntervalNanos;
                } else {
                    nextTAT = currentTAT + config.emissionIntervalNanos;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public record RateLimiterConfig(long emissionIntervalNanos, long toleranceNanos, double permitsPerSecond) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RateLimiterConfig that = (RateLimiterConfig) o;
            return emissionIntervalNanos == that.emissionIntervalNanos &&
                    toleranceNanos == that.toleranceNanos &&
                    Double.compare(that.permitsPerSecond, permitsPerSecond) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(emissionIntervalNanos, toleranceNanos, permitsPerSecond);
        }
    }
}
