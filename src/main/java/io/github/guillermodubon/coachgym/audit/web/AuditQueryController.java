package io.github.guillermodubon.coachgym.audit.web;

import io.github.guillermodubon.coachgym.audit.AuditEntryDetails;
import io.github.guillermodubon.coachgym.audit.AuditEntryPage;
import io.github.guillermodubon.coachgym.audit.AuditQueryValidationException;
import io.github.guillermodubon.coachgym.audit.AuditSearchQuery;
import io.github.guillermodubon.coachgym.audit.application.AuditQueryApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
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
                + "Metadata is default-deny sanitized; CSV export is deferred outside "
                + "the current scope.")
@SecurityRequirement(name = "sessionCookie")
class AuditQueryController {

    private final AuditQueryApplicationService service;

    AuditQueryController(AuditQueryApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "List audit entries",
            description = "Returns a bounded newest-first page of immutable audit summaries. "
                    + "Only administrators may access this read-only operation. Filters are exact "
                    + "and allowlisted; no arbitrary metadata or result filter is supported. "
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
    @ApiResponse(responseCode = "403", description = "Administrator role required")
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
            @RequestParam(defaultValue = "DESC") String direction) {
        AuditSearchQuery query = AuditSearchQuery.from(
                actorUserId,
                actorIdentifier,
                actionCode,
                resourceType,
                resourceId,
                resourceCode,
                correlationId,
                parseInstant(occurredFrom, "occurredFrom"),
                parseInstant(occurredUntil, "occurredUntil"),
                page,
                size,
                sort,
                direction);
        AuditEntryPage result = service.findAll(query);
        return AuditEntryPageResponse.from(result);
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
    @ApiResponse(responseCode = "403", description = "Administrator role required")
    @ApiResponse(responseCode = "404", description = "Audit entry not found")
    @ApiResponse(responseCode = "500", description = "Audit detail could not be read or projected")
    AuditEntryDetailsResponse findById(@PathVariable UUID auditEntryId) {
        AuditEntryDetails result = service.findById(auditEntryId);
        return AuditEntryDetailsResponse.from(result);
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
}
