package io.github.guillermodubon.coachgym.access.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.guillermodubon.coachgym.access.application.AccessApplicationService;
import io.github.guillermodubon.coachgym.access.application.AccessRecordNotFoundException;
import io.github.guillermodubon.coachgym.access.domain.AccessValidationException;
import java.util.UUID;

import io.github.guillermodubon.coachgym.shared.web.ApiProblemFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

class AccessErrorMappingTest {

    private AccessController controller;

    @BeforeEach
    void setUp() {
        controller =
                new AccessController(
                        mock(AccessApplicationService.class));
    }

    @Test
    void mapsAccessValidationExceptionToBadRequest() {
        ResponseEntity<ProblemDetail> response =
                controller.handleValidation(
                        new AccessValidationException(
                                "Invalid access request."));

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().getStatus())
                .isEqualTo(
                        HttpStatus.BAD_REQUEST.value());

        assertThat(response.getBody().getDetail())
                .isEqualTo(
                        "Invalid access request.");

        assertThat(response.getBody()
                .getProperties())
                .containsEntry(
                        "code",
                        "ACCESS_VALIDATION_FAILED");
    }

    @Test
    void mapsMissingAccessRecordToNotFound() {
        UUID recordId =
                UUID.fromString(
                        "40000000-0000-0000-0000-000000000099");

        ResponseEntity<ProblemDetail> response =
                controller.handleNotFound(
                        new AccessRecordNotFoundException(
                                recordId));

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().getStatus())
                .isEqualTo(
                        HttpStatus.NOT_FOUND.value());

        assertThat(response.getBody().getDetail())
                .contains(recordId.toString());

        assertThat(response.getBody()
                .getProperties())
                .containsEntry(
                        "code",
                        "ACCESS_RECORD_NOT_FOUND");
    }
}
