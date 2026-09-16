package io.github.guillermodubon.coachgym.notification.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.notification.application.EmailDeliveryNotFoundException;
import io.github.guillermodubon.coachgym.notification.domain.EmailDeliveryValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class EmailDeliveryProblemHandlerTest {

    private final EmailDeliveryProblemHandler handler = new EmailDeliveryProblemHandler();

    @Test
    void mapsValidationWithoutReturningExceptionDetails() {
        ResponseEntity<?> response = handler.handleValidation(
                new EmailDeliveryValidationException("recipient secret must not leak"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(org.springframework.http.ProblemDetail.class);
        org.springframework.http.ProblemDetail problem =
                (org.springframework.http.ProblemDetail) response.getBody();
        assertThat(problem.getProperties()).containsEntry("code", "EMAIL_DELIVERY_VALIDATION_FAILED");
        assertThat(problem.getDetail()).doesNotContain("secret");
    }

    @Test
    void mapsMissingDeliveryToNotFound() {
        ResponseEntity<?> response = handler.handleNotFound(
                new EmailDeliveryNotFoundException(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        org.springframework.http.ProblemDetail problem =
                (org.springframework.http.ProblemDetail) response.getBody();
        assertThat(problem.getProperties()).containsEntry("code", "EMAIL_DELIVERY_NOT_FOUND");
    }
}
