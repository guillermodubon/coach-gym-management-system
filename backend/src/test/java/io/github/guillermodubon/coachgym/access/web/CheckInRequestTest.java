package io.github.guillermodubon.coachgym.access.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.access.application.CheckInCommand;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CheckInRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation
                .buildDefaultValidatorFactory()
                .getValidator();
    }

    @Test
    void convertsToCommandWithoutNormalizingIdentifier() {
        CheckInRequest request =
                new CheckInRequest(
                        "  mem-000001  ");

        CheckInCommand command =
                request.toCommand();

        assertThat(command.rawIdentifier())
                .isEqualTo("  mem-000001  ");
    }

    @Test
    void acceptsIdentifierAtMaximumLength() {
        CheckInRequest request =
                new CheckInRequest(
                        "A".repeat(64));

        assertThat(validator.validate(request))
                .isEmpty();
    }

    @Test
    void rejectsNullIdentifier() {
        CheckInRequest request =
                new CheckInRequest(null);

        assertThat(validator.validate(request))
                .extracting(
                        violation ->
                                violation.getPropertyPath()
                                        .toString())
                .contains("identifier");
    }

    @Test
    void rejectsBlankIdentifier() {
        CheckInRequest request =
                new CheckInRequest("   ");

        assertThat(validator.validate(request))
                .extracting(
                        violation ->
                                violation.getPropertyPath()
                                        .toString())
                .contains("identifier");
    }

    @Test
    void rejectsIdentifierLongerThanMaximum() {
        CheckInRequest request =
                new CheckInRequest(
                        "A".repeat(65));

        assertThat(validator.validate(request))
                .extracting(
                        violation ->
                                violation.getPropertyPath()
                                        .toString())
                .contains("identifier");
    }
}
