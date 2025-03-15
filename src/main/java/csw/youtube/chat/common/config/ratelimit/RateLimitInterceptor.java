package csw.youtube.chat.common.config.ratelimit;


import csw.youtube.chat.common.annotation.RateLimit;
import csw.youtube.chat.user.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Aspect
@Configuration
public class RateLimitInterceptor {

    private final GcraRateLimiter gcraRateLimiter;

    @Autowired
    public RateLimitInterceptor(GcraRateLimiter gcraRateLimiter) {
        this.gcraRateLimiter = gcraRateLimiter;
    }

    @Around("@annotation(rateLimit)") // Intercept methods with @RateLimit annotation
    public Object intercept(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String baseKey = rateLimit.key();
        double permitsPerSecond = rateLimit.permitsPerSecond();
        Duration tolerance = Duration.of(rateLimit.tolerance(), rateLimit.toleranceUnit());

        // *** Dynamic Key Generation for Per-User Rate Limiting ***
        String userId = getCurrentUserId();
        String dynamicKey = baseKey + "_" + userId; // e.g., "register_123"

        // Configure rate limiter if not already configured or if configuration changed (optional, for dynamic updates)
        gcraRateLimiter.configureRateLimiter(dynamicKey, permitsPerSecond, tolerance);

        if (gcraRateLimiter.allowRequest(dynamicKey)) {
            return joinPoint.proceed(); // Proceed with the method execution
        } else {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
            // System.out.println("Rate limit exceeded for: " + methodName + ", key: " + dynamicKey); // Logging rate limiting

            // *** Add custom headers to the 429 response ***
            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                HttpServletResponse response = requestAttributes.getResponse();
                if (response != null) {
                    GcraRateLimiter.RateLimiterConfig config = gcraRateLimiter.getRateLimiterConfig(dynamicKey);
                    if (config != null) {
                        response.setHeader("X-RateLimit-Limit", String.valueOf(config.permitsPerSecond())); // Or calculate based on interval

                        // Calculate dynamic Retry-After based on emissionIntervalNanos
                        long retryAfterNanos = config.emissionIntervalNanos();
                        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(retryAfterNanos); // Convert to seconds
                        if (retryAfterSeconds < 1) {
                            retryAfterSeconds = 1; // Minimum 1 second (or fraction?)
                        }
                        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
                    }
                }
            }
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        }
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getId().toString();
        }

        HttpServletRequest request = ((ServletRequestAttributes) Objects.requireNonNull(RequestContextHolder.getRequestAttributes())).getRequest();
        String ipAddress = request.getRemoteAddr();
        return "user_" + ipAddress.hashCode();
    }
}