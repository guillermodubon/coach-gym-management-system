package io.github.guillermodubon.coachgym.maintenance;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Aggregate incident snapshot and event counts, without incident or staff details. */
public record IncidentReportingSummary(
        long totalIncidents,
        long openIncidents,
        long createdInRange,
        long resolvedInRange,
        Map<IncidentStatus, Long> countsByStatus,
        Map<IncidentPriority, Long> countsByPriority) {

    public IncidentReportingSummary {
        if (totalIncidents < 0 || openIncidents < 0 || createdInRange < 0 || resolvedInRange < 0) {
            throw new IllegalArgumentException("Incident reporting counts must be non-negative.");
        }
        countsByStatus = normalize(countsByStatus, IncidentStatus.class, "Incident status");
        countsByPriority = normalize(countsByPriority, IncidentPriority.class, "Incident priority");
        if (countsByStatus.values().stream().mapToLong(Long::longValue).sum() != totalIncidents
                || countsByPriority.values().stream().mapToLong(Long::longValue).sum() != totalIncidents
                || countsByStatus.get(IncidentStatus.OPEN)
                        + countsByStatus.get(IncidentStatus.IN_PROGRESS) != openIncidents) {
            throw new IllegalArgumentException("Incident status and priority counts must reconcile.");
        }
    }

    public static IncidentReportingSummary empty() {
        return new IncidentReportingSummary(0, 0, 0, 0, Map.of(), Map.of());
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
