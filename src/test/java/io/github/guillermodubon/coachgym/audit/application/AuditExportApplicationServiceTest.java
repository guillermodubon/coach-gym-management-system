package io.github.guillermodubon.coachgym.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.guillermodubon.coachgym.audit.AuditExportActor;
import io.github.guillermodubon.coachgym.audit.AuditExportCompleted;
import io.github.guillermodubon.coachgym.audit.AuditExportPolicy;
import io.github.guillermodubon.coachgym.audit.AuditExportQuery;
import io.github.guillermodubon.coachgym.audit.AuditExportResult;
import io.github.guillermodubon.coachgym.audit.AuditExportRow;
import io.github.guillermodubon.coachgym.audit.AuditExportSink;
import io.github.guillermodubon.coachgym.audit.AuditExportStreamException;
import io.github.guillermodubon.coachgym.audit.AuditExportDataAccessException;
import io.github.guillermodubon.coachgym.audit.AuditExportLimitExceededException;
import io.github.guillermodubon.coachgym.audit.AuditEntryQuery;
import io.github.guillermodubon.coachgym.audit.AuditMetadataProjection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.io.StringWriter;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AuditExportApplicationServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-09-17T12:34:56Z");
    private static final Instant FROM = Instant.parse("2026-09-17T00:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-09-17T12:00:00Z");

    @Mock private AuditEntryQuery auditEntryQuery;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AuditExportApplicationService service;
    private AuditExportQuery query;
    private AuditExportActor actor;

    @BeforeEach
    void setUp() {
        service = new AuditExportApplicationService(
                auditEntryQuery,
                eventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new AuditExportPolicy(Duration.ofDays(31), 10));
        query = new AuditExportQuery(
                null, "admin.user", "CLIENT_REGISTERED", "CLIENT", null,
                null, null, FROM, UNTIL, null, null);
        actor = new AuditExportActor(ACTOR_ID, "admin.user");
    }

    @Test
    void isAdminOnlyAndUsesReadOnlyTransaction() throws Exception {
        var method = AuditExportApplicationService.class.getMethod(
                "export", io.github.guillermodubon.coachgym.audit.AuditExportQuery.class,
                AuditExportActor.class, AuditExportSink.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
        assertThat(method.getAnnotation(Transactional.class).readOnly()).isTrue();
    }

    @Test
    void validatesBeforeQueryOrWriterAndDoesNotAuditInvalidRequests() {
        AuditExportQuery invalid = new AuditExportQuery(
                null, null, null, null, null, null, null,
                FROM, FROM.plus(Duration.ofDays(32)), null, null);

        assertThatThrownBy(() -> service.export(invalid, actor, row -> { }))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(auditEntryQuery, eventPublisher);
    }

    @Test
    void streamsRowsWithoutBufferingAndReturnsServerGeneratedMetadata() {
        AuditExportRow row = row();
        willAnswer(invocation -> {
            AuditExportSink sink = invocation.getArgument(2);
            sink.accept(row);
            return null;
        }).given(auditEntryQuery).streamExport(any(), any(), any());

        var received = new java.util.ArrayList<AuditExportRow>();
        AuditExportResult result = service.export(query, actor, received::add);

        assertThat(received).containsExactly(row);
        assertThat(result.rowsExported()).isEqualTo(1);
        assertThat(result.filename()).isEqualTo("audit-export-20260917-123456Z.csv");
        assertThat(result.mediaType()).isEqualTo("text/csv;charset=UTF-8");
        verify(eventPublisher).publishEvent(any(AuditExportCompleted.class));
    }

    @Test
    void coordinatesTheSanitizedStreamWithTheIncrementalCsvWriters() {
        willAnswer(invocation -> {
            invocation.<AuditExportSink>getArgument(2).accept(row());
            return null;
        }).given(auditEntryQuery).streamExport(any(), any(), any());

        StringWriter writer = new StringWriter();
        AuditExportResult result = service.exportCsv(query, actor, writer);

        assertThat(result.rowsExported()).isEqualTo(1);
        assertThat(writer.toString())
                .startsWith("entry_id,occurred_at,actor_user_id,actor_identifier,")
                .contains("CLIENT_REGISTERED")
                .endsWith("\r\n");
    }

    @Test
    void emptyExportIsSuccessfulAndAuditedWithZeroRows() {
        willAnswer(invocation -> null)
                .given(auditEntryQuery).streamExport(any(), any(), any());

        AuditExportResult result = service.export(query, actor, row -> {
            throw new AssertionError("Empty export must not invoke the sink.");
        });

        assertThat(result.rowsExported()).isZero();
        ArgumentCaptor<AuditExportCompleted> event =
                ArgumentCaptor.forClass(AuditExportCompleted.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().rowCount()).isZero();
    }

    @Test
    void writerFailureDoesNotPublishFalseSuccessAudit() {
        AuditExportRow row = row();
        willAnswer(invocation -> {
            invocation.<AuditExportSink>getArgument(2).accept(row);
            return null;
        }).given(auditEntryQuery).streamExport(any(), any(), any());

        assertThatThrownBy(() -> service.export(query, actor, ignored -> {
            throw new IllegalStateException("writer detail must not escape");
        })).isInstanceOf(AuditExportStreamException.class)
                .hasMessage("Audit export could not be streamed.");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void limitAndDataAccessFailuresDoNotPublishFalseSuccessAudit() {
        willAnswer(invocation -> {
            throw new AuditExportLimitExceededException(10);
        }).given(auditEntryQuery).streamExport(any(), any(), any());

        assertThatThrownBy(() -> service.export(query, actor, row -> { }))
                .isInstanceOf(AuditExportLimitExceededException.class);

        willAnswer(invocation -> {
            throw new AuditExportDataAccessException(new IllegalStateException());
        }).given(auditEntryQuery).streamExport(any(), any(), any());

        assertThatThrownBy(() -> service.export(query, actor, row -> { }))
                .isInstanceOf(AuditExportDataAccessException.class)
                .hasMessage("Audit export data could not be read.");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void auditEventContainsFilterNamesButNoFilterValuesOrExportRows() {
        willAnswer(invocation -> null)
                .given(auditEntryQuery).streamExport(any(), any(), any());

        service.export(query, actor, row -> { });

        ArgumentCaptor<AuditExportCompleted> event =
                ArgumentCaptor.forClass(AuditExportCompleted.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().filterSummary())
                .isEqualTo("actorIdentifier,actionCode,resourceType,occurredFrom,occurredUntil");
        assertThat(event.getValue().filterSummary())
                .doesNotContain("admin.user", "CLIENT_REGISTERED", "CLIENT");
        assertThat(event.getValue().format()).isEqualTo("CSV");
    }

    private static AuditExportRow row() {
        return new AuditExportRow(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                FROM,
                ACTOR_ID,
                "staff",
                "CLIENT_REGISTERED",
                "CLIENT",
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "CLI-000001",
                "Client registered.",
                null,
                new AuditMetadataProjection(Map.of("status", "ACTIVE"), false));
    }
}
