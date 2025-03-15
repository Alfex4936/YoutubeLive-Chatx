package csw.youtube.chat.common.annotation;


import csw.youtube.chat.common.config.ratelimit.RateLimitInterceptor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * Annotation to apply rate limiting to specific methods or controllers.
 * Uses the Generic Cell Rate Algorithm (GCRA) for efficient and robust rate limiting.
 */
@Target({ElementType.METHOD, ElementType.TYPE}) // Can be used on methods or classes
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * Key to identify the rate limiter configuration.
     * This key is used to store and retrieve the rate limiter settings.
     * It allows for defining different rate limits for various resources or operations.
     * <p>
     * In a production setting, consider using keys that are:
     * <ul>
     *     <li><b>Specific:</b> Reflect the resource or operation being rate-limited (e.g., "registerUser", "api-endpoint-X").</li>
     *     <li><b>Dynamic (if needed):</b>  Can be combined with user identifiers or IP addresses in the {@link RateLimitInterceptor}
     *         to create per-user or per-IP rate limits (e.g., "api-endpoint-X_user_{userId}").</li>
     * </ul>
     * p>
     * Defaults to "default" if no key is specified, applying a global rate limit if used without dynamic key generation.
     *
     * @return The key for the rate limiter.
     */
    String key() default "default";

    /**
     * The number of permits allowed per second (or other time unit, effectively requests per second).
     * This defines the sustained rate of requests that are permitted.
     * <p>
     * <b>Important Concepts: Permits and Tolerance (Burst Capacity)</b>
     * <p>
     * Imagine a bucket that leaks at a rate of {@code permitsPerSecond} and has a certain capacity (tolerance).
     * Each incoming request tries to take a "permit" from the bucket.
     * If there are permits available (bucket is not empty beyond its tolerance), the request is allowed, and permits are "used".
     * If there are no permits available (bucket is too empty, considering tolerance), the request is rate-limited (rejected).
     * <p>
     * <b>Example:</b> {@code permitsPerSecond = 10} means that, on average, 10 requests are allowed per second.
     *
     * @return The number of permits per second. Must be a positive value.
     */
    double permitsPerSecond();

    /**
     * The tolerance duration value, defining the burst capacity.
     * This allows for temporary bursts of requests exceeding the {@link #permitsPerSecond} for a short period.
     * <p>
     * <b>Tolerance as Burst Capacity:</b>
     * Tolerance determines how much "burstiness" is allowed. A higher tolerance allows for larger bursts of traffic.
     * It's conceptually similar to the "bucket size" or "cell delay variation tolerance" in the GCRA algorithm description.
     * <p>
     * <b>Example:</b> {@code tolerance = 500} and {@code toleranceUnit = ChronoUnit.MILLIS} means a tolerance of 500 milliseconds.
     * This allows for requests to arrive slightly faster than the average rate for a short duration.
     * <p>
     * A tolerance of 0 means no burst capacity; requests are strictly limited to the {@link #permitsPerSecond} rate.
     *
     * @return The tolerance duration value. Defaults to 0 (no tolerance). Must be non-negative.
     */
    long tolerance() default 0;

    /**
     * The unit of time for the {@link #tolerance} duration.
     * Specifies whether the {@link #tolerance} value is in milliseconds, seconds, minutes, etc.
     * <p>
     * Commonly used units are {@link ChronoUnit#MILLIS} or {@link ChronoUnit#SECONDS}.
     *
     * @return The ChronoUnit for the tolerance duration. Defaults to {@link ChronoUnit#MILLIS}.
     */
    ChronoUnit toleranceUnit() default ChronoUnit.MILLIS;
}