package io.github.guillermodubon.coachgym.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceDetails;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStatus;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceNotificationLookupServiceTest {

    @Mock private MaintenanceStore maintenanceStore;

    @Test
    void exposesOnlyMinimumNotificationRoutingProjection() {
        UUID maintenanceId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID assignedId = UUID.randomUUID();
        when(maintenanceStore.findById(maintenanceId)).thenReturn(Optional.of(
                new MaintenanceDetails(
                        maintenanceId, 1L, "MNT-000001",
                        equipmentId, "EQP-000001", "Treadmill",
                        null, null, MaintenanceType.PREVENTIVE,
                        MaintenanceStatus.SCHEDULED, LocalDate.of(2026, 9, 10),
                        null, null, "Provider", "Technician",
                        new BigDecimal("100.00"), null, "USD", null, "Notes",
                        creatorId, assignedId, null,
                        Instant.parse("2026-09-05T12:00:00Z"),
                        Instant.parse("2026-09-05T12:00:00Z"), 0L)));

        var result = new MaintenanceNotificationLookupService(maintenanceStore)
                .findById(maintenanceId).orElseThrow();

        assertThat(result.createdByUserId()).isEqualTo(creatorId);
        assertThat(result.assignedToUserId()).isEqualTo(assignedId);
        assertThat(result.maintenanceCode()).isEqualTo("MNT-000001");
    }
}
