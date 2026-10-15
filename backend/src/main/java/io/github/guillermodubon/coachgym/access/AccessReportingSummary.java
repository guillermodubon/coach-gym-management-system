package io.github.guillermodubon.coachgym.access;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Safe aggregate of physical access attempts; it contains no client or credential data. */
public record AccessReportingSummary(
        long totalAttempts,
        long allowedAttempts,
        long deniedAttempts,
        BigDecimal allowedRate,
        long manualAttempts,
        long qrAttempts,
        long unknownSourceAttempts,
        Map<AccessReasonCode, Long> denialReasonCounts) {

    public AccessReportingSummary {
        nonNegative(totalAttempts, "Total access attempts");
        nonNegative(allowedAttempts, "Allowed access attempts");
        nonNegative(deniedAttempts, "Denied access attempts");
        nonNegative(manualAttempts, "Manual access attempts");
        nonNegative(qrAttempts, "QR access attempts");
        nonNegative(unknownSourceAttempts, "Unknown-source access attempts");
        if (Math.addExact(allowedAttempts, deniedAttempts) != totalAttempts) {
            throw new IllegalArgumentException("Allowed and denied attempts must reconcile to total attempts.");
        }
        if (Math.addExact(Math.addExact(manualAttempts, qrAttempts), unknownSourceAttempts)
                != totalAttempts) {
            throw new IllegalArgumentException("Access source counts must reconcile to total attempts.");
        }
        Objects.requireNonNull(allowedRate, "Allowed access rate is required.");
        if (allowedRate.signum() < 0 || allowedRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Allowed access rate must be between zero and one.");
        }

        Objects.requireNonNull(denialReasonCounts, "Access denial reason counts are required.");
        EnumMap<AccessReasonCode, Long> normalized = new EnumMap<>(AccessReasonCode.class);
        for (AccessReasonCode reason : AccessReasonCode.values()) {
            if (reason != AccessReasonCode.ACCESS_ALLOWED) {
                normalized.put(reason, 0L);
            }
        }
        denialReasonCounts.forEach((reason, count) -> {
            if (reason == null || reason == AccessReasonCode.ACCESS_ALLOWED || count == null || count < 0) {
                throw new IllegalArgumentException("Access denial reason counts are invalid.");
            }
            normalized.put(reason, count);
        });
        long reasonTotal = normalized.values().stream().mapToLong(Long::longValue).sum();
        if (reasonTotal != deniedAttempts) {
            throw new IllegalArgumentException("Denial reason counts must reconcile to denied attempts.");
        }
        denialReasonCounts = Collections.unmodifiableMap(normalized);
    }

    public static AccessReportingSummary empty() {
        return new AccessReportingSummary(
                0, 0, 0, BigDecimal.ZERO, 0, 0, 0, Map.of());
    }

    private static void nonNegative(long value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be non-negative.");
        }
    }
}
