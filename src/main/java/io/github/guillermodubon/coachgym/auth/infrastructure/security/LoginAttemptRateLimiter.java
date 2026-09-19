package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded failed-login guard for one application process.
 *
 * <p>This is deliberately not presented as distributed protection. A shared
 * limiter can be introduced when the approved deployment needs one, while the
 * database remains authoritative for account state.</p>
 */
public class LoginAttemptRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, FailureWindow> failures = new ConcurrentHashMap<>();

    public LoginAttemptRateLimiter(LoginRateLimitProperties properties, Clock clock) {
        this.maxAttempts = properties.maxAttempts();
        this.window = properties.window();
        this.clock = clock;
    }

    public boolean isAllowed(String identifier) {
        Instant now = clock.instant();
        String key = normalize(identifier);
        FailureWindow failureWindow = failures.get(key);
        if (failureWindow == null || isOutsideWindow(failureWindow, now)) {
            failures.remove(key, failureWindow);
            return true;
        }
        return failureWindow.count < maxAttempts;
    }

    public void recordFailure(String identifier) {
        Instant now = clock.instant();
        String key = normalize(identifier);
        failures.compute(key, (ignored, current) -> {
            if (current == null || isOutsideWindow(current, now)) {
                return new FailureWindow(now, 1);
            }
            return new FailureWindow(current.startedAt, current.count + 1);
        });
        prune(now);
    }

    public void clear(String identifier) {
        failures.remove(normalize(identifier));
    }

    public long retryAfterSeconds(String identifier) {
        FailureWindow failureWindow = failures.get(normalize(identifier));
        if (failureWindow == null) {
            return 1;
        }
        long remaining = window.minus(Duration.between(failureWindow.startedAt, clock.instant()))
                .toSeconds();
        return Math.max(1, remaining + 1);
    }

    private boolean isOutsideWindow(FailureWindow failureWindow, Instant now) {
        return !now.isBefore(failureWindow.startedAt.plus(window));
    }

    private void prune(Instant now) {
        failures.entrySet().removeIf(entry -> isOutsideWindow(entry.getValue(), now));
    }

    private static String normalize(String identifier) {
        return identifier == null ? "" : identifier.strip().toLowerCase(Locale.ROOT);
    }

    private record FailureWindow(Instant startedAt, int count) {
    }
}
