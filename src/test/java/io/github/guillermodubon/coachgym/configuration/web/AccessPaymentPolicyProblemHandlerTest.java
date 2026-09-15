package io.github.guillermodubon.coachgym.configuration.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyDataAccessException;
import io.github.guillermodubon.coachgym.configuration.application.AccessPaymentPolicyVersionConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class AccessPaymentPolicyProblemHandlerTest {

    private final AccessPaymentPolicyProblemHandler handler =
            new AccessPaymentPolicyProblemHandler();

    @Test
    void mapsVersionConflictsToSafeConflictProblem() {
        var response = handler.handleVersionConflict(
                new AccessPaymentPolicyVersionConflictException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull()
                .extracting(ProblemDetail::getDetail)
                .isEqualTo("The access-payment policy was modified by another operation.");
        assertThat(response.getBody().getProperties()).containsEntry(
                "code", "ACCESS_PAYMENT_POLICY_VERSION_CONFLICT");
    }

    @Test
    void mapsDataAccessFailuresWithoutLeakingCause() {
        var response = handler.handleDataAccessFailure(
                new AccessPaymentPolicyDataAccessException(
                        "database password leaked", new IllegalStateException("sql details")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getDetail())
                .isEqualTo("The access-payment policy operation could not be completed.");
        assertThat(response.getBody().getProperties())
                .containsEntry("code", "ACCESS_PAYMENT_POLICY_DATA_ACCESS_FAILED")
                .doesNotContainValue("database password leaked")
                .doesNotContainValue("sql details");
    }
}
