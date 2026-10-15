package io.github.guillermodubon.coachgym.access.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.access.application.QrAccessCheckInCommand;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class QrCheckInRequestTest {

    private static final String VALID_PAYLOAD =
            "cgac:v1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void convertsOnlyTheDecodedPayloadIntoTheApplicationCommand() {
        QrAccessCheckInCommand command = new QrCheckInRequest(VALID_PAYLOAD).toCommand();

        assertThat(command.payload().value()).isEqualTo(VALID_PAYLOAD);
    }

    @Test
    void acceptsTheCanonicalPayloadAtItsMaximumLength() {
        assertThat(validator.validate(new QrCheckInRequest(VALID_PAYLOAD))).isEmpty();
    }

    @Test
    void rejectsMissingBlankOversizedAndControlCharacterPayloads() {
        assertThat(validator.validate(new QrCheckInRequest(null)))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("payload");
        assertThat(validator.validate(new QrCheckInRequest("   ")))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("payload");
        assertThat(validator.validate(new QrCheckInRequest("A".repeat(52))))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("payload");
        assertThat(validator.validate(new QrCheckInRequest(VALID_PAYLOAD + "\n")))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("payload");
    }
}
