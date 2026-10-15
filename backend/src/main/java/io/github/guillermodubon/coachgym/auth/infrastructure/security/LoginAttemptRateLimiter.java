package io.github.guillermodubon.coachgym.auth.infrastructure.security;

import io.github.guillermodubon.coachgym.auth.application.LoginAttemptGuard;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bounded failed-login guard for one application process.
 *
 * <p>The number of distinct identifiers retained is capped. At capacity,
 * failures for previously unseen identifiers are not retained; those logins
 * are not blocked by this per-identifier limiter. This avoids turning
 * high-cardinality unauthenticated traffic into a lockout of unrelated
 * accounts. The memory bound is process-local and is not distributed rate
 * limiting.</p>
 */
public class LoginAttemptRateLimiter implements LoginAttemptGuard {

    private final int maxAttempts;
    private final int maxTrackedIdentifiers;
    private final Duration window;
    private final Clock clock;
    private final LinkedHashMap<String, FailureWindow> failures = new LinkedHashMap<>();
    private final NavigableMap<Instant, Set<String>> identifiersByExpiry = new TreeMap<>();

    public LoginAttemptRateLimiter(LoginRateLimitProperties properties, Clock clock) {
        this.maxAttempts = properties.maxAttempts();
        this.maxTrackedIdentifiers = properties.maxTrackedIdentifiers();
        this.window = properties.window();
        this.clock = clock;
    }

    public synchronized boolean isAllowed(String identifier) {
        Instant now = clock.instant();
        pruneExpired(now);
        FailureWindow failureWindow = failures.get(normalize(identifier));
        return failureWindow == null || failureWindow.count < maxAttempts;
    }

    public synchronized void recordFailure(String identifier) {
        Instant now = clock.instant();
        pruneExpired(now);
        String key = normalize(identifier);
        FailureWindow current = failures.get(key);
        if (current == null) {
            if (failures.size() < maxTrackedIdentifiers) {
                track(key, new FailureWindow(now, 1));
            }
            return;
        }
        int nextCount = current.count >= maxAttempts ? maxAttempts : current.count + 1;
        failures.put(key, new FailureWindow(current.startedAt, nextCount));
    }

    public synchronized void clear(String identifier) {
        String key = normalize(identifier);
        FailureWindow current = failures.remove(key);
        if (current != null) {
            removeFromExpiryIndex(key, current);
        }
    }

    public synchronized long retryAfterSeconds(String identifier) {
        Instant now = clock.instant();
        pruneExpired(now);
        FailureWindow failureWindow = failures.get(normalize(identifier));
        if (failureWindow == null) {
            return 1;
        }
        long remaining = Duration.between(now, failureWindow.startedAt.plus(window)).toSeconds();
        return Math.max(1, remaining + 1);
    }

    private void pruneExpired(Instant now) {
        while (!identifiersByExpiry.isEmpty() && !identifiersByExpiry.firstKey().isAfter(now)) {
            Map.Entry<Instant, Set<String>> expired = identifiersByExpiry.pollFirstEntry();
            for (String key : expired.getValue()) {
                FailureWindow failureWindow = failures.get(key);
                if (failureWindow != null
                        && failureWindow.startedAt.plus(window).equals(expired.getKey())) {
                    failures.remove(key);
                }
            }
        }
    }

    private void track(String key, FailureWindow failureWindow) {
        failures.put(key, failureWindow);
        identifiersByExpiry.computeIfAbsent(
                failureWindow.startedAt.plus(window), ignored -> new HashSet<>()).add(key);
    }

    private void removeFromExpiryIndex(String key, FailureWindow failureWindow) {
        Instant expiresAt = failureWindow.startedAt.plus(window);
        Set<String> keys = identifiersByExpiry.get(expiresAt);
        if (keys == null) {
            return;
        }
        keys.remove(key);
        if (keys.isEmpty()) {
            identifiersByExpiry.remove(expiresAt);
        }
    }

    private static String normalize(String identifier) {
        return identifier == null ? "" : identifier.strip().toLowerCase(Locale.ROOT);
    }

    private record FailureWindow(Instant startedAt, int count) {
    }
}
