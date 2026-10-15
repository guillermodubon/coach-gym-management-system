package io.github.guillermodubon.coachgym.notification;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Safe aggregate of persisted durable email deliveries and transport attempts. */
public record EmailDeliveryReportingSummary(
        long totalDeliveries,
        long pendingDeliveries,
        long sentDeliveries,
        long failedDeliveries,
        long retryAttempts,
        Map<EmailDeliveryType, Long> deliveriesByType,
        Map<EmailDeliveryFailureCode, Long> failuresByCode) {

    public EmailDeliveryReportingSummary {
        if (totalDeliveries < 0 || pendingDeliveries < 0 || sentDeliveries < 0
                || failedDeliveries < 0 || retryAttempts < 0
                || Math.addExact(Math.addExact(pendingDeliveries, sentDeliveries), failedDeliveries)
                        != totalDeliveries) {
            throw new IllegalArgumentException("Email delivery counts must be non-negative and reconcile.");
        }
        deliveriesByType = normalize(deliveriesByType, EmailDeliveryType.class, "Delivery type");
        failuresByCode = normalize(failuresByCode, EmailDeliveryFailureCode.class, "Failure code");
        if (deliveriesByType.values().stream().mapToLong(Long::longValue).sum()
                != totalDeliveries) {
            throw new IllegalArgumentException("Delivery type counts must reconcile to logical deliveries.");
        }
    }

    public static EmailDeliveryReportingSummary empty() {
        return new EmailDeliveryReportingSummary(0, 0, 0, 0, 0, Map.of(), Map.of());
    }

    private static <E extends Enum<E>> Map<E, Long> normalize(
            Map<E, Long> values, Class<E> enumType, String label) {
        Objects.requireNonNull(values, label + " counts are required.");
        EnumMap<E, Long> normalized = new EnumMap<>(enumType);
        for (E key : enumType.getEnumConstants()) {
            normalized.put(key, 0L);
        }
        values.forEach((key, count) -> {
            if (key == null || count == null || count < 0) {
                throw new IllegalArgumentException(label + " counts are invalid.");
            }
            normalized.put(key, count);
        });
        return Collections.unmodifiableMap(normalized);
    }
}
