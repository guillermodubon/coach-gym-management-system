package io.github.guillermodubon.coachgym.user.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.user.application.StaffDirectoryUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class StaffDirectoryProblemContractTest {

    @Test
    void directoryUnavailableProblemDoesNotExposePersistenceDetailsAndDisablesCaching() {
        StaffIdentityProblemHandler handler = new StaffIdentityProblemHandler(
                new MockHttpServletRequest("GET", "/api/v1/staff"));

        ResponseEntity<ProblemDetail> response = handler.directoryUnavailable(
                new StaffDirectoryUnavailableException(
                        new IllegalStateException("private SQL and database credential")));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst("Referrer-Policy"))
                .isEqualTo("no-referrer");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "STAFF_DIRECTORY_UNAVAILABLE");
        assertThat(response.getBody().getDetail())
                .isEqualTo("The staff directory is temporarily unavailable.")
                .doesNotContain("SQL", "credential");
        assertThat(response.getBody().toString())
                .doesNotContain("private SQL", "database credential");
    }
}
