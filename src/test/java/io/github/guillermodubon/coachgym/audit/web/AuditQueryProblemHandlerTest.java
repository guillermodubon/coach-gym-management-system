package io.github.guillermodubon.coachgym.audit.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.audit.AuditQueryValidationException;
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
