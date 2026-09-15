package io.github.guillermodubon.coachgym.accesscredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessCredentialResolverContractTest {

    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000901");
    private static final UUID CLIENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000902");

    @Test
    void resolvedProjectionContainsOnlyAuthoritativeActiveIdentity() {
        ResolvedAccessCredential resolved = new ResolvedAccessCredential(
                CREDENTIAL_ID,
                CLIENT_ID,
                AccessCredentialStatus.ACTIVE);

        assertThat(resolved.credentialId()).isEqualTo(CREDENTIAL_ID);
        assertThat(resolved.clientId()).isEqualTo(CLIENT_ID);
        assertThat(resolved.status()).isEqualTo(AccessCredentialStatus.ACTIVE);
        assertThat(resolved.toString())
                .doesNotContain("token", "payload", "fingerprint", "storage");
    }

    @Test
    void inactiveProjectionCannotCrossTheResolverBoundary() {
        assertThatThrownBy(() -> new ResolvedAccessCredential(
                CREDENTIAL_ID,
                CLIENT_ID,
                AccessCredentialStatus.REVOKED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only active credentials can be resolved.");
    }

    @Test
    void resolverContractIsMinimalAndUsesOptionalResolution() throws Exception {
        var resolve = AccessCredentialResolver.class.getMethod(
                "resolve", AccessCredentialQrPayload.class);

        assertThat(resolve.getReturnType()).isEqualTo(java.util.Optional.class);
        assertThat(resolve.getParameterTypes())
                .containsExactly(AccessCredentialQrPayload.class);
    }
}
