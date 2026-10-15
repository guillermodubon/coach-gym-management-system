package io.github.guillermodubon.coachgym.maintenance.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.maintenance.IncidentDetails;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriority;
import io.github.guillermodubon.coachgym.maintenance.IncidentStatus;
import io.github.guillermodubon.coachgym.maintenance.application.IncidentPage;
import io.github.guillermodubon.coachgym.maintenance.domain.IncidentValidationException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IncidentWebContractTest {

    private static final UUID INCIDENT_ID = UUID.randomUUID();
    private static final UUID EQUIPMENT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-03T03:00:00Z");

    @Test
    void reportRequestCreatesNormalizedCommand() {
        ReportIncidentRequest request = new ReportIncidentRequest(
                EQUIPMENT_ID, IncidentPriority.HIGH,
                "  Motor failure.  ", true, 3L);
        var command = request.toCommand();
        assertThat(command.description()).isEqualTo("Motor failure.");
        assertThat(command.equipmentVersion()).isEqualTo(3L);
    }

    @Test
    void responseMapsAllAuthoritativeFields() {
        IncidentResponse response = IncidentResponse.from(details());
        assertThat(response.id()).isEqualTo(INCIDENT_ID);
        assertThat(response.incidentCode()).isEqualTo("INC-000001");
        assertThat(response.equipmentCode()).isEqualTo("EQP-000001");
        assertThat(response.status()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    void pageResponseMapsItemsAndMetadata() {
        IncidentPageResponse response = IncidentPageResponse.from(
                new IncidentPage(List.of(details()), 0, 25, 1, 1));
        assertThat(response.items()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void invalidWithdrawalWithoutVersionIsRejected() {
        assertThatThrownBy(() -> new ReportIncidentRequest(
                EQUIPMENT_ID, IncidentPriority.CRITICAL,
                "Risk.", true, null).toCommand())
                .isInstanceOf(IncidentValidationException.class)
                .hasMessageContaining("Equipment version is required");
    }

    private static IncidentDetails details() {
        return new IncidentDetails(
                INCIDENT_ID, 1L, "INC-000001", EQUIPMENT_ID,
                "EQP-000001", "Treadmill", IncidentStatus.OPEN,
                IncidentPriority.HIGH, "Motor failure.", NOW,
                ACTOR_ID, null, null, null, null,
                NOW, NOW, 0L);
    }
}
