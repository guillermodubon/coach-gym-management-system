package io.github.guillermodubon.coachgym.client.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.client.application.ClientPhotoNotFoundException;
import io.github.guillermodubon.coachgym.client.application.ClientPhotoValidationException;
import io.github.guillermodubon.coachgym.client.application.ClientStateConflictException;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class ClientProfileProblemContractTest {

    @Test
    void adviceIsRestrictedToTheNewClientControllers() {
        RestControllerAdvice advice =
                ClientProfileProblemHandler.class.getAnnotation(
                        RestControllerAdvice.class);

        assertThat(advice).isNotNull();
        assertThat(advice.assignableTypes())
                .containsExactlyInAnyOrder(
                        ClientProfileController.class,
                        ClientPhotoController.class);
    }

    @Test
    void handlersExposeSafeProblemDetails() throws Exception {
        Method photoNotFound = ClientProfileProblemHandler.class
                .getDeclaredMethod(
                        "handleClientPhotoNotFound",
                        ClientPhotoNotFoundException.class);
        Method validation = ClientProfileProblemHandler.class
                .getDeclaredMethod(
                        "handleClientValidation",
                        RuntimeException.class);
        Method conflict = ClientProfileProblemHandler.class
                .getDeclaredMethod(
                        "handleClientStateConflict",
                        ClientStateConflictException.class);

        assertThat(photoNotFound.getReturnType()).isEqualTo(ProblemDetail.class);
        assertThat(validation.getReturnType()).isEqualTo(ProblemDetail.class);
        assertThat(conflict.getReturnType()).isEqualTo(ProblemDetail.class);
    }

    @Test
    void handlerDeclaresNoSecretBearingFields() {
        String declaredFields = Arrays.stream(
                        ClientProfileProblemHandler.class.getDeclaredFields())
                .map(field -> field.getName().toLowerCase())
                .collect(java.util.stream.Collectors.joining(" "));

        assertThat(declaredFields)
                .doesNotContain("password")
                .doesNotContain("token")
                .doesNotContain("storagekey")
                .doesNotContain("content");
    }
}
