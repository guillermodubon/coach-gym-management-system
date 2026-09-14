package io.github.guillermodubon.coachgym.accesscredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccessCredentialQrPayloadTest {

    private static final String ZERO_TOKEN =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String ZERO_PAYLOAD = "cgac:v1:" + ZERO_TOKEN;

    @Test
    void composesAndParsesTheVersionedOpaquePayload() {
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.fromToken(ZERO_TOKEN);

        assertThat(payload.value()).isEqualTo(ZERO_PAYLOAD);
        assertThat(payload.version()).isEqualTo("v1");
        assertThat(payload.value()).startsWith("cgac:v1:");
        assertThat(payload.value()).hasSize(AccessCredentialQrPayload.MAX_LENGTH);
        assertThat(AccessCredentialQrPayload.parse("  " + ZERO_PAYLOAD + "  "))
                .isEqualTo(payload);
    }

    @Test
    void payloadDoesNotContainClientInformationOrRevealTheTokenInDiagnostics() {
        AccessCredentialQrPayload payload = AccessCredentialQrPayload.fromToken(ZERO_TOKEN);

        assertThat(payload.value())
                .doesNotContain("Ana", "Martinez", "@", "+503", "client");
        assertThat(payload.toString())
                .contains("version=v1")
                .doesNotContain(ZERO_TOKEN, ZERO_PAYLOAD);
    }

    @Test
    void rejectsMalformedOrNonCanonicalPayloadsWithoutEchoingInput() {
        String malformed = "cgac:v1:not-a-token";

        assertThatThrownBy(() -> AccessCredentialQrPayload.parse(malformed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Access credential QR payload is invalid.")
                .hasMessageNotContaining(malformed);
        assertThatThrownBy(() -> AccessCredentialQrPayload.fromToken("A".repeat(44)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Access credential QR payload is invalid.");
        assertThatThrownBy(() -> AccessCredentialQrPayload.parse(
                "cgac:v2:" + ZERO_TOKEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccessCredentialQrPayload.parse(
                "cgac:v1:" + ZERO_TOKEN + "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
