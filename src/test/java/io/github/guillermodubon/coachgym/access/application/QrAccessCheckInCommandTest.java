package io.github.guillermodubon.coachgym.access.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.access.domain.QrAccessPayloadValidationException;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialQrPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class QrAccessCheckInCommandTest {

    private static final String TOKEN =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String PAYLOAD = "cgac:v1:" + TOKEN;

    @Test
    void parsesAndCanonicalizesTheApprovedPayload() {
        QrAccessCheckInCommand command =
                QrAccessCheckInCommand.parse("  " + PAYLOAD + "  ");

        assertThat(command.payload().value()).isEqualTo(PAYLOAD);
        assertThat(command.payload().version()).isEqualTo("v1");
    }

    @Test
    void commandRequiresAParsedPayload() {
        assertThatThrownBy(() -> new QrAccessCheckInCommand(null))
                .isInstanceOf(QrAccessPayloadValidationException.class)
                .hasMessage("QR access payload is invalid.");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "other:v1:" + TOKEN,
            "cgac:v2:" + TOKEN,
            "cgac:v1:not-a-token",
            "cgac:v1:" + TOKEN + "x"
    })
    void rejectsMalformedUnsupportedOrUnsafePayloads(String rawPayload) {
        assertThatThrownBy(() -> QrAccessCheckInCommand.parse(rawPayload))
                .isInstanceOf(QrAccessPayloadValidationException.class)
                .hasMessage("QR access payload is invalid.");
    }

    @Test
    void validationFailureNeverEchoesThePresentedPayload() {
        String malformed = "cgac:v1:secret-value-that-must-not-appear";

        assertThatThrownBy(() -> QrAccessCheckInCommand.parse(malformed))
                .isInstanceOf(QrAccessPayloadValidationException.class)
                .hasMessageNotContaining(malformed)
                .hasMessageNotContaining("secret-value");
    }

    @Test
    void rejectsControlCharactersWithoutEchoingThem() {
        String malformed = "cgac:v1:" + TOKEN.substring(0, 42) + "\u0000";

        assertThatThrownBy(() -> QrAccessCheckInCommand.parse(malformed))
                .isInstanceOf(QrAccessPayloadValidationException.class)
                .hasMessage("QR access payload is invalid.")
                .hasMessageNotContaining("\u0000");
    }

    @Test
    void commandDiagnosticsRedactOpaqueTokenMaterial() {
        QrAccessCheckInCommand command = QrAccessCheckInCommand.parse(PAYLOAD);

        assertThat(command.toString())
                .contains("AccessCredentialQrPayload")
                .doesNotContain(TOKEN, PAYLOAD);
        assertThat(command.payload()).isInstanceOf(AccessCredentialQrPayload.class);
    }

    @Test
    void commandContractCarriesNoPaymentPolicyOrFrameworkTypes() {
        assertThat(QrAccessCheckInCommand.class.getRecordComponents())
                .hasSize(1);
        assertThat(QrAccessCheckInCommand.class.getRecordComponents()[0].getType())
                .isEqualTo(AccessCredentialQrPayload.class);
    }
}
