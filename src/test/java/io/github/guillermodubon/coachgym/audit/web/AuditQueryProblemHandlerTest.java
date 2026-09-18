package io.github.guillermodubon.coachgym.audit.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.audit.AuditQueryValidationException;
import io.github.guillermodubon.coachgym.audit.AuditExportAuditException;
import io.github.guillermodubon.coachgym.audit.AuditExportDataAccessException;
import io.github.guillermodubon.coachgym.audit.AuditExportLimitExceededException;
import io.github.guillermodubon.coachgym.audit.AuditExportStreamException;
import io.github.guillermodubon.coachgym.audit.AuditExportValidationException;
import io.github.guillermodubon.coachgym.audit.application.AuditEntryNotFoundException;
import io.github.guillermodubon.coachgym.audit.application.AuditMetadataProjectionException;
import io.github.guillermodubon.coachgym.audit.application.AuditQueryDataAccessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

class AuditQueryProblemHandlerTest {

    private final AuditQueryProblemHandler handler = new AuditQueryProblemHandler();

    @Test
    void mapsValidationToSafeProblemDetail() {
        ResponseEntity<ProblemDetail> response = handler.handleValidation(
                new AuditQueryValidationException("secret-filter-value"));

        assertProblem(response, HttpStatus.BAD_REQUEST,
                "AUDIT_QUERY_VALIDATION_FAILED",
                "The audit query is invalid.");
    }

    @Test
    void mapsNotFoundWithoutIncludingTheIdentifier() {
        ResponseEntity<ProblemDetail> response = handler.handleNotFound(
                new AuditEntryNotFoundException(UUID.randomUUID()));

        assertProblem(response, HttpStatus.NOT_FOUND,
                "AUDIT_ENTRY_NOT_FOUND",
                "The requested audit entry was not found.");
    }

    @Test
    void mapsDataAccessAndProjectionFailuresWithoutTheirCauses() {
        ResponseEntity<ProblemDetail> dataAccess = handler.handleDataAccess(
                new AuditQueryDataAccessException(
                        "sql must not escape", new IllegalStateException("table")));
        ResponseEntity<ProblemDetail> projection = handler.handleProjection(
                new AuditMetadataProjectionException(
                        "raw metadata must not escape", new IllegalStateException("json")));

        assertProblem(dataAccess, HttpStatus.INTERNAL_SERVER_ERROR,
                "AUDIT_QUERY_DATA_ACCESS_FAILED",
                "Audit entries could not be read.");
        assertProblem(projection, HttpStatus.INTERNAL_SERVER_ERROR,
                "AUDIT_METADATA_PROJECTION_FAILED",
                "Audit metadata could not be projected safely.");
        assertThat(dataAccess.getBody().toString()).doesNotContain("sql", "table");
        assertThat(projection.getBody().toString()).doesNotContain("raw", "json");
    }

    @Test
    void mapsExportFailuresToStablePrivacySafeProblems() {
        ResponseEntity<ProblemDetail> required = handler.handleExportValidation(
                new AuditExportValidationException(
                        AuditExportValidationException.RANGE_REQUIRED_CODE,
                        "secret range"));
        ResponseEntity<ProblemDetail> limit = handler.handleExportLimit(
                new AuditExportLimitExceededException(10));
        ResponseEntity<ProblemDetail> dataAccess = handler.handleExportDataAccess(
                new AuditExportDataAccessException(
                        new IllegalStateException("SQL must not escape")));
        ResponseEntity<ProblemDetail> stream = handler.handleExportFailure(
                new AuditExportStreamException(new IllegalStateException("payload")));
        ResponseEntity<ProblemDetail> audit = handler.handleExportFailure(
                new AuditExportAuditException(new IllegalStateException("metadata")));

        assertProblem(required, HttpStatus.BAD_REQUEST,
                "AUDIT_EXPORT_RANGE_REQUIRED",
                "Both occurredFrom and occurredUntil are required for an audit export.");
        assertProblem(limit, HttpStatus.BAD_REQUEST,
                "AUDIT_EXPORT_LIMIT_EXCEEDED",
                "The audit export exceeds the configured row limit.");
        assertProblem(dataAccess, HttpStatus.INTERNAL_SERVER_ERROR,
                "AUDIT_EXPORT_DATA_ACCESS_FAILED",
                "Audit export data could not be read.");
        assertProblem(stream, HttpStatus.INTERNAL_SERVER_ERROR,
                "AUDIT_EXPORT_STREAM_FAILED",
                "The audit export could not be completed.");
        assertProblem(audit, HttpStatus.INTERNAL_SERVER_ERROR,
                "AUDIT_EXPORT_AUDIT_FAILED",
                "The audit export could not be completed.");
        assertThat(required.getBody().toString()).doesNotContain("secret");
        assertThat(dataAccess.getBody().toString()).doesNotContain("SQL", "payload");
    }

    private static void assertProblem(
            ResponseEntity<ProblemDetail> response,
            HttpStatus status,
            String code,
            String detail) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .containsEntry("code", code);
        assertThat(response.getBody().getDetail()).isEqualTo(detail);
        assertThat(response.getBody().getType().toString())
                .isEqualTo("urn:coach-gym:problem:" + code.toLowerCase().replace('_', '-'));
    }
}
