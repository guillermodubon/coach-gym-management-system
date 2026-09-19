package io.github.guillermodubon.coachgym.audit.application;

import io.github.guillermodubon.coachgym.audit.AuditExportActor;
import io.github.guillermodubon.coachgym.audit.AuditExportAuditException;
import io.github.guillermodubon.coachgym.audit.AuditExportCompleted;
import io.github.guillermodubon.coachgym.audit.AuditExportPolicy;
import io.github.guillermodubon.coachgym.audit.AuditExportQuery;
import io.github.guillermodubon.coachgym.audit.AuditExportResult;
import io.github.guillermodubon.coachgym.audit.AuditExportSink;
import io.github.guillermodubon.coachgym.audit.AuditExportStreamException;
import io.github.guillermodubon.coachgym.audit.AuditExportValidationException;
import io.github.guillermodubon.coachgym.audit.AuditEntryQuery;
import io.github.guillermodubon.coachgym.audit.infrastructure.csv.AuditCsvHeaderWriter;
import io.github.guillermodubon.coachgym.audit.infrastructure.csv.AuditCsvRowWriter;
import java.io.IOException;
import java.io.Writer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ADMIN-only orchestration for bounded, streaming audit CSV exports. */
@Service
public class AuditExportApplicationService {

    public static final String CSV_MEDIA_TYPE = "text/csv;charset=UTF-8";

    private static final DateTimeFormatter FILENAME_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'")
                    .withZone(ZoneOffset.UTC);

    private final AuditEntryQuery auditEntryQuery;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final AuditExportPolicy policy;

    /** Uses the conservative application defaults for production wiring. */
    @Autowired
    public AuditExportApplicationService(
            AuditEntryQuery auditEntryQuery,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this(auditEntryQuery, eventPublisher, clock, AuditExportPolicy.defaults());
    }

    /** Constructor with an explicit policy for bounded configuration and tests. */
    public AuditExportApplicationService(
            AuditEntryQuery auditEntryQuery,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            AuditExportPolicy policy) {
        this.auditEntryQuery = Objects.requireNonNull(
                auditEntryQuery, "Audit entry query is required.");
        this.eventPublisher = Objects.requireNonNull(
                eventPublisher, "Event publisher is required.");
        this.clock = Objects.requireNonNull(clock, "Clock is required.");
        this.policy = Objects.requireNonNull(policy, "Audit export policy is required.");
    }

    /**
     * Streams sanitized rows to the caller-owned sink and returns safe download metadata.
     * The success audit is published only after the sink has consumed every row.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AuditExportResult export(
            AuditExportQuery query,
            AuditExportActor actor,
            AuditExportSink sink) {

        validateInputs(query, actor, sink);
        return streamValidated(query, actor, sink);
    }

    /**
     * Internal application-boundary adapter used by the later HTTP streaming
     * block. It never buffers bytes and does not make the CSV writer a public
     * module contract.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AuditExportResult exportCsv(
            AuditExportQuery query,
            AuditExportActor actor,
            Writer writer) {
        if (writer == null) {
            throw new AuditExportValidationException("Export writer is required.");
        }
        AuditCsvHeaderWriter headerWriter = new AuditCsvHeaderWriter();
        AuditCsvRowWriter rowWriter = new AuditCsvRowWriter();
        validateInputs(query, actor, row -> { });
        boolean[] headerWritten = {false};
        AuditExportResult result = streamValidated(query, actor, row -> {
            try {
                if (!headerWritten[0]) {
                    headerWriter.write(writer);
                    headerWritten[0] = true;
                }
                rowWriter.write(writer, row);
            } catch (IOException exception) {
                throw new AuditExportStreamException(exception);
            }
        }, () -> {
            try {
                if (!headerWritten[0]) {
                    headerWriter.write(writer);
                    headerWritten[0] = true;
                }
                writer.flush();
            } catch (IOException exception) {
                throw new AuditExportStreamException(exception);
            }
        });
        return result;
    }

    /**
     * Validates an export before the HTTP response is committed.
     *
     * <p>The controller uses this small application-boundary operation to
     * reject malformed or unbounded requests as a normal {@code ProblemDetail}
     * response. The actual query is validated again by the streaming path so
     * callers cannot bypass the policy by invoking a different overload.</p>
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public void validate(AuditExportQuery query) {
        requireQuery(query);
        query.validate(policy);
    }

    /** Returns a server-generated, header-safe filename for a new download. */
    @PreAuthorize("hasRole('ADMIN')")
    public String newFilename() {
        return filename(clock.instant());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public AuditExportResult exportCsv(
            AuditExportQuery query,
            UUID actorUserId,
            String actorIdentifier,
            Writer writer) {
        return exportCsv(
                query,
                new AuditExportActor(actorUserId, actorIdentifier),
                writer);
    }

    private AuditExportResult streamValidated(
            AuditExportQuery query,
            AuditExportActor actor,
            AuditExportSink sink) {
        return streamValidated(query, actor, sink, null);
    }

    private AuditExportResult streamValidated(
            AuditExportQuery query,
            AuditExportActor actor,
            AuditExportSink sink,
            Runnable beforeAudit) {
        query.validate(policy);

        Instant filenameTime = clock.instant();
        UUID exportId = UUID.randomUUID();
        long[] rowsExported = {0L};
        AuditExportSink guardedSink = row -> {
            try {
                sink.accept(row);
            } catch (RuntimeException exception) {
                throw new AuditExportStreamException(exception);
            }
            rowsExported[0]++;
        };

        auditEntryQuery.streamExport(query, policy, guardedSink);
        if (beforeAudit != null) {
            beforeAudit.run();
        }

        Instant occurredAt = clock.instant();
        try {
            eventPublisher.publishEvent(new AuditExportCompleted(
                    exportId,
                    actor.userId(),
                    actor.identifier(),
                    query.occurredFrom(),
                    query.occurredUntil(),
                    filterSummary(query),
                    query.sort(),
                    query.sortDirection(),
                    rowsExported[0],
                    policy.maxRows(),
                    AuditExportCompleted.FORMAT_CSV,
                    occurredAt));
        } catch (RuntimeException exception) {
            throw new AuditExportAuditException(exception);
        }

        return new AuditExportResult(
                rowsExported[0],
                query,
                filename(filenameTime),
                CSV_MEDIA_TYPE);
    }

    private void validateInputs(
            AuditExportQuery query,
            AuditExportActor actor,
            AuditExportSink sink) {
        requireQuery(query);
        if (actor == null) {
            throw new AuditExportValidationException("Export actor is required.");
        }
        if (sink == null) {
            throw new AuditExportValidationException("Export sink is required.");
        }
        query.validate(policy);
    }

    /** Convenience overload for callers that already hold the minimal actor fields. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public AuditExportResult export(
            AuditExportQuery query,
            UUID actorUserId,
            String actorIdentifier,
            AuditExportSink sink) {
        return export(query, new AuditExportActor(actorUserId, actorIdentifier), sink);
    }

    private static void requireQuery(AuditExportQuery query) {
        if (query == null) {
            throw new AuditExportValidationException("Audit export query is required.");
        }
    }

    private static String filename(Instant timestamp) {
        return "audit-export-" + FILENAME_TIME.format(timestamp) + ".csv";
    }

    /** Records only which approved filters were present, never their values. */
    private static String filterSummary(AuditExportQuery query) {
        List<String> filters = new ArrayList<>();
        if (query.actorUserId() != null) {
            filters.add("actorUserId");
        }
        if (query.actorIdentifier() != null) {
            filters.add("actorIdentifier");
        }
        if (query.actionCode() != null) {
            filters.add("actionCode");
        }
        if (query.resourceType() != null) {
            filters.add("resourceType");
        }
        if (query.resourceId() != null) {
            filters.add("resourceId");
        }
        if (query.resourceCode() != null) {
            filters.add("resourceCode");
        }
        if (query.correlationId() != null) {
            filters.add("correlationId");
        }
        filters.add("occurredFrom");
        filters.add("occurredUntil");
        return String.join(",", filters);
    }
}
