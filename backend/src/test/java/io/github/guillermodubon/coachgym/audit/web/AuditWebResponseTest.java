package io.github.guillermodubon.coachgym.audit.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.audit.AuditEntryDetails;
import io.github.guillermodubon.coachgym.audit.AuditEntryPage;
import io.github.guillermodubon.coachgym.audit.AuditEntrySummary;
import io.github.guillermodubon.coachgym.audit.AuditMetadataProjection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditWebResponseTest {

    @Test
    void mapsSummaryPagesToImmutableHttpResponses() {
        AuditEntrySummary summary = summary();
        AuditEntryPageResponse response = AuditEntryPageResponse.from(
                AuditEntryPage.of(List.of(summary), 0, 25, 1));

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo(summary.id());
        assertThatThrownBy(() -> response.items().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mapsOnlyTheSanitizedDetailProjectionAndKeepsItImmutable() {
        AuditEntryDetails details = new AuditEntryDetails(
                summary(),
                new AuditMetadataProjection(
                        Map.of("status", "SAFE"),
                        true));

        AuditEntryDetailsResponse response = AuditEntryDetailsResponse.from(details);

        assertThat(response.metadata()).containsEntry("status", "SAFE");
        assertThat(response.metadataRedacted()).isTrue();
        assertThatThrownBy(() -> response.metadata().put("status", "unsafe"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static AuditEntrySummary summary() {
        return new AuditEntrySummary(
                UUID.randomUUID(),
                "CLIENT_REGISTERED",
                "CLIENT",
                UUID.randomUUID(),
                "CLI-WEB-001",
                UUID.randomUUID(),
                "admin",
                "Client registered",
                Instant.parse("2026-09-16T12:00:00Z"),
                null);
    }
}
