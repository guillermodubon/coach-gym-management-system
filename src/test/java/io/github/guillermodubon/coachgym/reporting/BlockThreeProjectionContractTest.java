package io.github.guillermodubon.coachgym.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessOperationalDaySummary;
import io.github.guillermodubon.coachgym.access.AccessReportingSummary;
import io.github.guillermodubon.coachgym.equipment.EquipmentReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportingSummary;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceReportingSummary;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryReportingSummary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockThreeProjectionContractTest {

    @Test
    void emptySnapshotsContainStableZeroValuesAndCurrentCanonicalBuckets() {
        assertThat(AccessReportingSummary.empty().totalAttempts()).isZero();
        assertThat(AccessReportingSummary.empty().denialReasonCounts())
                .containsEntry(AccessReasonCode.DUPLICATE_CHECK_IN, 0L)
                .containsEntry(AccessReasonCode.PAYMENT_REQUIRED, 0L)
                .containsEntry(AccessReasonCode.MEMBERSHIP_NOT_VALID_AT_BRANCH, 0L);
        assertThat(EquipmentReportingSummary.empty().countsByStatus()).hasSize(4);
        assertThat(IncidentReportingSummary.empty().countsByStatus()).hasSize(3);
        assertThat(MaintenanceReportingSummary.empty().countsByStatus()).hasSize(4);
        assertThat(EmailDeliveryReportingSummary.empty().deliveriesByType()).hasSize(2);
        assertThat(EmailDeliveryReportingSummary.empty().failuresByCode()).isNotEmpty();
    }

    @Test
    void rejectsInconsistentTotalsAndKeepsAggregateMapsImmutable() {
        assertThatThrownBy(() -> new AccessReportingSummary(
                1, 1, 0, BigDecimal.ONE, 1, 0, 0,
                Map.of(AccessReasonCode.PAYMENT_REQUIRED, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EquipmentReportingSummary(2, 0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailDeliveryReportingSummary(
                1, 0, 1, 0, 0, Map.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> EquipmentReportingSummary.empty().countsByStatus().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> IncidentReportingSummary.empty().countsByPriority().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void receptionistAccessProjectionExposesOnlyOperationalDayCounts() {
        AccessOperationalDaySummary summary = new AccessOperationalDaySummary(
                LocalDate.of(2026, 1, 1), ZoneId.of("America/El_Salvador"), 3, 2, 1);

        assertThat(Arrays.stream(AccessOperationalDaySummary.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("day", "timezone", "totalAttempts", "allowedAttempts", "deniedAttempts");
        assertThat(summary.toString())
                .doesNotContain("reason", "source", "credential", "client", "token");
    }
}
