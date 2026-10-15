package io.github.guillermodubon.coachgym.plan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlanDetails(
        UUID id,
        String planCode,
        String name,
        String description,
        int durationValue,
        DurationUnit durationUnit,
        BigDecimal listPrice,
        String currency,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long version) {
}
