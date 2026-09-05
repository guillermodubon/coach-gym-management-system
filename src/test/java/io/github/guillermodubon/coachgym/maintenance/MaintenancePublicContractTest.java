package io.github.guillermodubon.coachgym.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintenancePublicContractTest {

    private static final UUID MAINTENANCE_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID EQUIPMENT_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID INCIDENT_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT =
            Instant.parse("2026-09-03T18:00:00Z");

    @Test
    void representsCompleteCorrectiveWorkOrderProjection() {
        MaintenanceDetails details = new MaintenanceDetails(
                MAINTENANCE_ID,
                1L,
                "MNT-000001",
                EQUIPMENT_ID,
                "EQP-000001",
                "Commercial Treadmill",
                INCIDENT_ID,
                "INC-000001",
                MaintenanceType.CORRECTIVE,
                MaintenanceStatus.SCHEDULED,
                LocalDate.of(2026, 9, 8),
                null,
                null,
                "External Service Provider",
                "Technical Contact",
                new BigDecimal("125.00"),
                null,
                "USD",
                null,
                "Inspect motor controller.",
                ACTOR_ID,
                null,
                null,
                CREATED_AT,
                CREATED_AT,
                0L);

        assertThat(details.id()).isEqualTo(MAINTENANCE_ID);
        assertThat(details.maintenanceCode()).isEqualTo("MNT-000001");
        assertThat(details.equipmentCode()).isEqualTo("EQP-000001");
        assertThat(details.incidentCode()).isEqualTo("INC-000001");
        assertThat(details.maintenanceType())
                .isEqualTo(MaintenanceType.CORRECTIVE);
        assertThat(details.status()).isEqualTo(MaintenanceStatus.SCHEDULED);
        assertThat(details.estimatedCost())
                .isEqualByComparingTo("125.00");
        assertThat(details.version()).isZero();
    }

    @Test
    void representsInitialAppendOnlyHistoryEntry() {
        UUID historyId =
                UUID.fromString("50000000-0000-0000-0000-000000000001");

        MaintenanceStatusHistoryDetails history =
                new MaintenanceStatusHistoryDetails(
                        historyId,
                        MAINTENANCE_ID,
                        null,
                        MaintenanceStatus.SCHEDULED,
                        "Maintenance scheduled.",
                        CREATED_AT,
                        ACTOR_ID);

        assertThat(history.id()).isEqualTo(historyId);
        assertThat(history.maintenanceId()).isEqualTo(MAINTENANCE_ID);
        assertThat(history.previousStatus()).isNull();
        assertThat(history.newStatus())
                .isEqualTo(MaintenanceStatus.SCHEDULED);
        assertThat(history.reason()).isEqualTo("Maintenance scheduled.");
    }

    @Test
    void equipmentOutcomeExcludesImplicitRetirement() {
        assertThat(EquipmentMaintenanceOutcome.values())
                .containsExactly(
                        EquipmentMaintenanceOutcome.AVAILABLE,
                        EquipmentMaintenanceOutcome.OUT_OF_SERVICE);
    }
}
