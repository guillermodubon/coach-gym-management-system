package io.github.guillermodubon.coachgym.accesscredential.infrastructure.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class AccessCredentialTokenAdapterTest {

    private static final String ZERO_TOKEN =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String ZERO_PAYLOAD = "cgac:v1:" + ZERO_TOKEN;

    @Test
    void generatorUsesUrlSafe256BitTokens() {
        SecureRandomAccessCredentialTokenGenerator generator =
                new SecureRandomAccessCredentialTokenGenerator(new SecureRandom());

        String first = generator.generate();
        String second = generator.generate();

        assertThat(first)
                .hasSize(43)
                .matches("[A-Za-z0-9_-]{43}");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void generatorHasADeterministicTestSeamWithoutChangingProductionDefaults() {
        SecureRandom fixed = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                java.util.Arrays.fill(bytes, (byte) 0);
            }
        };

        SecureRandomAccessCredentialTokenGenerator generator =
                new SecureRandomAccessCredentialTokenGenerator(fixed);

        assertThat(generator.generate()).isEqualTo(ZERO_TOKEN);
    }

    @Test
    void protectorProducesKnownSha256FingerprintAndConstantTimeMatch() {
        Sha256AccessCredentialTokenProtector protector =
                new Sha256AccessCredentialTokenProtector();

        assertThat(protector.schemeVersion()).isEqualTo("sha256-v1");
        assertThat(protector.fingerprint(ZERO_PAYLOAD))
                .isEqualTo("ac7bdf3321b19d438edd68068d9493eeac98a6681629a1d8f6ea079023b81764");
        assertThat(protector.fingerprint(ZERO_PAYLOAD)).doesNotContain(ZERO_TOKEN);
        assertThat(protector.matches(
                "  " + ZERO_PAYLOAD + "  ",
                "ac7bdf3321b19d438edd68068d9493eeac98a6681629a1d8f6ea079023b81764"))
                .isTrue();
        assertThat(protector.matches(ZERO_PAYLOAD, "f".repeat(64))).isFalse();
    }

    @Test
    void protectorRejectsMalformedPayloadWithoutEchoingSecretMaterial() {
        Sha256AccessCredentialTokenProtector protector =
                new Sha256AccessCredentialTokenProtector();
        String malformed = "cgac:v1:not-a-token";

        assertThatThrownBy(() -> protector.fingerprint(malformed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credential token payload is invalid.")
                .hasMessageNotContaining(malformed);
        assertThat(protector.matches(malformed, "a".repeat(64))).isFalse();
    }
}
