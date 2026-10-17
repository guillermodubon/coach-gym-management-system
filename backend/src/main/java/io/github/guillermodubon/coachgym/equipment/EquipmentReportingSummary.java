package io.github.guillermodubon.coachgym.equipment;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Current equipment-status snapshot without resource or staff details. */
public record EquipmentReportingSummary(
        long totalEquipment,
        long outOfServiceEquipment,
        Map<EquipmentStatus, Long> countsByStatus) {

    public EquipmentReportingSummary {
        Objects.requireNonNull(countsByStatus, "Equipment status counts are required.");
        EnumMap<EquipmentStatus, Long> normalized = new EnumMap<>(EquipmentStatus.class);
        for (EquipmentStatus status : EquipmentStatus.values()) {
            normalized.put(status, 0L);
        }
        countsByStatus.forEach((status, count) -> {
            if (status == null || count == null || count < 0) {
                throw new IllegalArgumentException("Equipment status counts are invalid.");
            }
            normalized.put(status, count);
        });
        if (totalEquipment < 0 || outOfServiceEquipment < 0
                || normalized.values().stream().mapToLong(Long::longValue).sum() != totalEquipment
                || normalized.get(EquipmentStatus.OUT_OF_SERVICE) != outOfServiceEquipment) {
            throw new IllegalArgumentException("Equipment status counts must reconcile to the total.");
        }
        countsByStatus = Collections.unmodifiableMap(normalized);
    }

    public static EquipmentReportingSummary empty() {
        return new EquipmentReportingSummary(0, 0, Map.of());
    }
}
