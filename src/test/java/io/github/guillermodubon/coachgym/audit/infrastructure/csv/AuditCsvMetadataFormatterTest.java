package io.github.guillermodubon.coachgym.audit.infrastructure.csv;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.audit.AuditExportRow;
import io.github.guillermodubon.coachgym.audit.AuditMetadataProjection;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditCsvMetadataFormatterTest {

    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID RESOURCE_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private final AuditCsvMetadataFormatter formatter = new AuditCsvMetadataFormatter();

    @Test
    void formatsSanitizedMetadataWithStableKeyAndNestedValueOrdering() {
        AuditMetadataProjection projection = new AuditMetadataProjection(
                Map.of(
                        "zeta", "last",
                        "alpha", Map.of("z", "last", "a", "first"),
                        "items", List.of("one", "two")),
                true);

        assertThat(formatter.format(projection)).isEqualTo(
                "{\"_redacted\":true,\"alpha\":{\"a\":\"first\",\"z\":\"last\"},"
                        + "\"items\":[\"one\",\"two\"],\"zeta\":\"last\"}");
    }

    @Test
    void escapesMetadataTextAndBoundsItsOutput() {
        AuditMetadataProjection projection = new AuditMetadataProjection(
                Map.of("description", "line1\nline2\t\"quoted\""), false);

        assertThat(formatter.format(projection))
                .isEqualTo("{\"description\":\"line1\\nline2\\t\\\"quoted\\\"\"}");

        AuditMetadataProjection large = new AuditMetadataProjection(
                Map.of("description", "x".repeat(AuditCsvMetadataFormatter.MAX_METADATA_LENGTH + 100)),
                false);
        assertThat(formatter.format(large))
                .hasSize(AuditCsvMetadataFormatter.MAX_METADATA_LENGTH)
                .endsWith("...[truncated]");
    }

    @Test
    void rowWriterUsesOnlyProjectedMetadataAndKeepsTheWriterOpen() throws Exception {
        AuditExportRow row = new AuditExportRow(
                UUID.randomUUID(),
                OCCURRED_AT,
                ACTOR_ID,
                "admin.user",
                "CLIENT_REGISTERED",
                "CLIENT",
                RESOURCE_ID,
                "CLI-001",
                "Client, registered\nwith note",
                null,
                new AuditMetadataProjection(Map.of("status", "ACTIVE"), false));
        StringWriter writer = new StringWriter();

        new AuditCsvRowWriter().write(writer, row);

        assertThat(writer.toString())
                .isEqualTo(
                        row.entryId()
                                + ",2026-01-01T00:00:00Z,"
                                + ACTOR_ID
                                + ",admin.user,CLIENT_REGISTERED,CLIENT,"
                                + RESOURCE_ID
                                + ",CLI-001,\"Client, registered\nwith note\",,"
                                + "\"{\"\"status\"\":\"\"ACTIVE\"\"}\"\r\n")
                .doesNotContain("metadata_json", "password", "token", "secret");
    }
}
