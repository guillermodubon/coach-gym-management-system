package io.github.guillermodubon.coachgym.audit.web;

import io.github.guillermodubon.coachgym.audit.AuditExportActor;
import io.github.guillermodubon.coachgym.audit.AuditExportStreamException;
import io.github.guillermodubon.coachgym.audit.AuditExportQuery;
import io.github.guillermodubon.coachgym.audit.AuditExportValidationException;
import io.github.guillermodubon.coachgym.audit.AuditEntryDetails;
import io.github.guillermodubon.coachgym.audit.AuditEntryPage;
import io.github.guillermodubon.coachgym.audit.AuditQueryValidationException;
import io.github.guillermodubon.coachgym.audit.AuditSearchQuery;
import io.github.guillermodubon.coachgym.audit.application.AuditExportApplicationService;
import io.github.guillermodubon.coachgym.audit.application.AuditExportMetrics;
import io.github.guillermodubon.coachgym.audit.application.AuditQueryApplicationService;
import io.github.guillermodubon.coachgym.auth.CoachGymUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin ADMIN-only HTTP boundary for immutable audit-history queries. */
@RestController
@RequestMapping("/api/v1/audit-entries")
@Tag(
        name = "Audit history",
        description = "ADMIN-only, read-only inspection of immutable audit history. "
                + "Metadata is default-deny sanitized; bounded CSV export uses the "
                + "same scope authorization, filters, and privacy policy as audit queries.")
@SecurityRequirement(name = "sessionCookie")
class AuditQueryController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditQueryController.class);

    private final AuditQueryApplicationService service;
    private final AuditExportApplicationService exportService;
    private final AuditExportMetrics exportMetrics;

    AuditQueryController(
            AuditQueryApplicationService service,
            AuditExportApplicationService exportService,
            AuditExportMetrics exportMetrics) {
        this.service = service;
        this.exportService = exportService;
        this.exportMetrics = exportMetrics;
    }

    @GetMapping
    @Operation(
            summary = "List audit entries",
            description = "Returns a bounded newest-first page of immutable audit summaries. "
                    + "ADMIN users may query only the organization or branches allowed by their "
                    + "current persisted staff scope. Branch IDs are filters, never authority. "
                    + "Filters are exact and allowlisted; result is ALLOWED or DENIED, and no "
                    + "arbitrary metadata filter is supported. "
                    + "Date bounds are inclusive ISO-8601 instants and may span at most 366 days. "
                    + "Page is zero-based, size is between 1 and 100, and the only sort field is "
                    + "OCCURRED_AT with the audit ID used as a deterministic tie-breaker.",
            security = @SecurityRequirement(name = "sessionCookie"))
    @ApiResponse(
            responseCode = "200",
            description = "Audit summaries returned",
            content = @Content(schema = @Schema(implementation = AuditEntryPageResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid audit filter, pagination, or sorting")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "ADMIN role and authorized persisted scope required")
    @ApiResponse(responseCode = "500", description = "Audit history could not be read")
    AuditEntryPageResponse findAll(
            @Parameter(description = "Exact actor user UUID")
            @RequestParam(required = false) UUID actorUserId,
            @Parameter(description = "Exact actor identifier, normalized case-insensitively")
            @RequestParam(required = false) String actorIdentifier,
            @Parameter(description = "Exact allowlisted action code")
            @RequestParam(required = false) String actionCode,
            @Parameter(description = "Exact allowlisted resource type")
            @RequestParam(required = false) String resourceType,
            @Parameter(description = "Exact persisted result: ALLOWED or DENIED")
            @RequestParam(required = false) String result,
            @Parameter(description = "Requested canonical branch UUIDs; authorization is resolved independently")
            @RequestParam(required = false) Set<UUID> branchIds,
            @Parameter(description = "Exact resource UUID")
            @RequestParam(required = false) UUID resourceId,
            @Parameter(description = "Exact resource-code snapshot")
            @RequestParam(required = false) String resourceCode,
            @Parameter(description = "Exact correlation UUID")
            @RequestParam(required = false) UUID correlationId,
            @Parameter(description = "Inclusive lower ISO-8601 instant bound")
            @RequestParam(required = false) String occurredFrom,
            @Parameter(description = "Inclusive upper ISO-8601 instant bound")
            @RequestParam(required = false) String occurredUntil,
            @Parameter(description = "Zero-based page index; default 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size from 1 to 100; default 25")
            @RequestParam(defaultValue = "25") int size,
            @Parameter(description = "Only OCCURRED_AT is supported")
            @RequestParam(defaultValue = "OCCURRED_AT") String sort,
            @Parameter(description = "ASC or DESC; default DESC")
            @RequestParam(defaultValue = "DESC") String direction,
            Authentication authentication) {
        AuditSearchQuery query = AuditSearchQuery.from(
                actorUserId,
                actorIdentifier,
                actionCode,
                resourceType,
                result,
                resourceId,
                resourceCode,
                correlationId,
                branchIds == null ? Set.of() : branchIds,
                parseInstant(occurredFrom, "occurredFrom"),
                parseInstant(occurredUntil, "occurredUntil"),
                page,
                size,
                sort,
                direction);
        AuditEntryPage pageResult = service.findAll(query, actor(authentication).userId());
        return AuditEntryPageResponse.from(pageResult);
    }

    @GetMapping("/{auditEntryId}")
    @Operation(
            summary = "Get an audit entry",
            description = "Returns one immutable audit entry for administrators. "
                    + "Metadata is a bounded, default-deny sanitized projection; raw JSONB, "
                    + "credentials, provider payloads, diagnostics, and live domain data are never returned.",
            security = @SecurityRequirement(name = "sessionCookie"))
    @ApiResponse(
            responseCode = "200",
            description = "Sanitized audit detail returned",
            content = @Content(schema = @Schema(implementation = AuditEntryDetailsResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid audit-entry UUID")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "ADMIN role and authorized persisted scope required")
    @ApiResponse(responseCode = "404", description = "Audit entry not found")
    @ApiResponse(responseCode = "500", description = "Audit detail could not be read or projected")
    AuditEntryDetailsResponse findById(
            @PathVariable UUID auditEntryId,
            Authentication authentication) {
        AuditEntryDetails result = service.findById(
                auditEntryId, actor(authentication).userId());
        return AuditEntryDetailsResponse.from(result);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Download a bounded audit CSV export",
            description = "Streams a UTF-8 CSV using the same current persisted scope rules as "
                    + "audit list and detail queries. ADMIN + ORGANIZATION may export organization-wide "
                    + "or explicitly selected canonical branches; ADMIN + BRANCH must provide a "
                    + "non-empty set of actively assigned branches; RECEPTIONIST and anonymous users "
                    + "are denied. Branch IDs are filters, never authority, and SQL applies the "
                    + "authoritatively resolved scope before rows are streamed. Uses exact allowlisted "
                    + "audit filters including result and branchIds. occurredFrom and occurredUntil are required "
                    + "inclusive UTC instants; the range is limited to 31 days by default and "
                    + "the result is rejected when it exceeds the server maximum of 10,000 rows. "
                    + "Columns are fixed and include only persisted branch IDs; metadata is sanitized "
                    + "with the audit default-deny policy, and spreadsheet formula values are neutralized. "
                    + "The fixed CSV header includes branch_id, sourced only from persisted audit attribution. "
                    + "A successful export audit record is written after streaming, so it cannot appear "
                    + "in its own export, but may appear in a later export whose date range includes it. "
                    + "No export file is stored on the server.",
            security = @SecurityRequirement(name = "sessionCookie"))
    @ApiResponse(
            responseCode = "200",
            description = "UTF-8 CSV attachment with fixed sanitized columns",
            content = @Content(
                    mediaType = "text/csv",
                    schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "400", description = "Invalid filters, range, or export limit")
    @ApiResponse(responseCode = "401", description = "Authentication required")
    @ApiResponse(responseCode = "403", description = "ADMIN role and authorized persisted organization or branch scope required")
    @ApiResponse(responseCode = "500", description = "The export could not be prepared or streamed")
    void exportCsv(
            @Parameter(description = "Exact actor user UUID")
            @RequestParam(required = false) String actorUserId,
            @Parameter(description = "Exact actor identifier, normalized case-insensitively")
            @RequestParam(required = false) String actorIdentifier,
            @Parameter(description = "Exact allowlisted action code")
            @RequestParam(required = false) String actionCode,
            @Parameter(description = "Exact allowlisted resource type")
            @RequestParam(required = false) String resourceType,
            @Parameter(description = "Exact persisted result: ALLOWED or DENIED")
            @RequestParam(required = false) String result,
            @Parameter(description = "Requested canonical branch UUIDs; authorization is resolved independently")
            @RequestParam(required = false) Set<UUID> branchIds,
            @Parameter(description = "Exact resource UUID")
            @RequestParam(required = false) String resourceId,
            @Parameter(description = "Exact resource-code snapshot")
            @RequestParam(required = false) String resourceCode,
            @Parameter(description = "Exact correlation UUID")
            @RequestParam(required = false) String correlationId,
            @Parameter(required = true, description = "Inclusive lower UTC ISO-8601 instant")
            @RequestParam(required = false) String occurredFrom,
            @Parameter(required = true, description = "Inclusive upper UTC ISO-8601 instant")
            @RequestParam(required = false) String occurredUntil,
            @Parameter(description = "Only OCCURRED_AT is supported")
            @RequestParam(defaultValue = "OCCURRED_AT") String sort,
            @Parameter(description = "ASC or DESC; default DESC")
            @RequestParam(defaultValue = "DESC") String direction,
            Authentication authentication,
            HttpServletResponse response) {
        AuditExportQuery query = exportQuery(
                parseUuid(actorUserId, "actorUserId"),
                actorIdentifier,
                actionCode,
                resourceType,
                result,
                branchIds == null ? Set.of() : branchIds,
                parseUuid(resourceId, "resourceId"),
                resourceCode,
                parseUuid(correlationId, "correlationId"),
                parseExportInstant(occurredFrom, "occurredFrom"),
                parseExportInstant(occurredUntil, "occurredUntil"),
                sort,
                direction);

        AuditExportActor actor = actor(authentication);
        exportService.validate(query, actor.userId());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.parseMediaType(
                AuditExportApplicationService.CSV_MEDIA_TYPE).toString());
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\""
                        + exportService.newFilename(query, actor.userId()) + "\"");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");

        try {
            Writer writer = response.getWriter();
            exportService.exportCsv(query, actor, writer);
            exportMetrics.recordSuccess();
        } catch (IOException exception) {
            exportMetrics.recordFailure();
            handleCommittedFailure(response, "Audit CSV response could not be written.");
            if (!response.isCommitted()) {
                throw new AuditExportStreamException(exception);
            }
        } catch (RuntimeException exception) {
            exportMetrics.recordFailure();
            if (response.isCommitted()) {
                handleCommittedFailure(response, "Audit CSV response terminated after commitment.");
                return;
            }
            throw exception;
        }
    }

    private static Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException exception) {
            throw new AuditQueryValidationException(
                    "Invalid " + field + " timestamp.");
        }
    }

    private static Instant parseExportInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException exception) {
            throw new AuditExportValidationException(
                    AuditExportValidationException.DEFAULT_CODE,
                    "An export timestamp is invalid.");
        }
    }

    private static AuditExportQuery exportQuery(
            UUID actorUserId,
            String actorIdentifier,
            String actionCode,
            String resourceType,
            String result,
            Set<UUID> branchIds,
            UUID resourceId,
            String resourceCode,
            UUID correlationId,
            Instant occurredFrom,
            Instant occurredUntil,
            String sort,
            String direction) {
        try {
            return AuditExportQuery.from(
                    actorUserId,
                    actorIdentifier,
                    actionCode,
                    resourceType,
                    result,
                    resourceId,
                    resourceCode,
                    correlationId,
                    branchIds,
                    occurredFrom,
                    occurredUntil,
                    sort,
                    direction);
        } catch (AuditQueryValidationException exception) {
            throw new AuditExportValidationException(
                    AuditExportValidationException.DEFAULT_CODE,
                    "The audit export request is invalid.");
        }
    }

    private static UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.strip());
        } catch (IllegalArgumentException exception) {
            throw new AuditExportValidationException(
                    AuditExportValidationException.DEFAULT_CODE,
                    "An export filter is invalid.");
        }
    }

    private static AuditExportActor actor(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof CoachGymUserPrincipal principal)) {
            throw new IllegalStateException("Authenticated staff principal is required.");
        }
        return new AuditExportActor(principal.id(), principal.getUsername());
    }

    private static void handleCommittedFailure(HttpServletResponse response, String message) {
        if (response.isCommitted()) {
            LOGGER.warn(message);
        }
    }
}
