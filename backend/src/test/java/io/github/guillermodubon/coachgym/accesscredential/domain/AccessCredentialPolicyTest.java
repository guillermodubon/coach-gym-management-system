package io.github.guillermodubon.coachgym.accesscredential.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialStatus;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialEligibilityException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialStateConflictException;
import io.github.guillermodubon.coachgym.accesscredential.application.AccessCredentialValidationException;
import io.github.guillermodubon.coachgym.client.ClientStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessCredentialPolicyTest {

    private static final UUID CLIENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000821");
    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000822");

    @Test
    void onlyActiveClientsMayReceiveCredentials() {
        assertThat(AccessCredentialPolicy.isIssueEligible(ClientStatus.ACTIVE)).isTrue();
        assertThat(AccessCredentialPolicy.isIssueEligible(ClientStatus.INACTIVE)).isFalse();
        assertThatCode(() -> AccessCredentialPolicy.requireIssueAllowed(
                CLIENT_ID, ClientStatus.ACTIVE))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> AccessCredentialPolicy.requireIssueAllowed(
                CLIENT_ID, ClientStatus.INACTIVE))
                .isInstanceOf(AccessCredentialEligibilityException.class)
                .satisfies(error -> {
                    AccessCredentialEligibilityException exception =
                            (AccessCredentialEligibilityException) error;
                    assertThat(exception.clientId()).isEqualTo(CLIENT_ID);
                    assertThat(exception.currentStatus()).isEqualTo(ClientStatus.INACTIVE);
                });
    }

    @Test
    void revocationAndReplacementRequireAnActiveCredential() {
        assertThatCode(() -> AccessCredentialPolicy.requireRevocationAllowed(
                CREDENTIAL_ID, AccessCredentialStatus.ACTIVE))
                .doesNotThrowAnyException();
        assertThatCode(() -> AccessCredentialPolicy.requireReplacementAllowed(
                CREDENTIAL_ID, AccessCredentialStatus.ACTIVE))
                .doesNotThrowAnyException();
        assertThat(AccessCredentialPolicy.isFinal(AccessCredentialStatus.REVOKED)).isTrue();
        assertThat(AccessCredentialPolicy.isFinal(AccessCredentialStatus.ACTIVE)).isFalse();

        assertThatThrownBy(() -> AccessCredentialPolicy.requireReplacementAllowed(
                CREDENTIAL_ID, AccessCredentialStatus.REVOKED))
                .isInstanceOf(AccessCredentialStateConflictException.class)
                .satisfies(error -> {
                    AccessCredentialStateConflictException conflict =
                            (AccessCredentialStateConflictException) error;
                    assertThat(conflict.credentialId()).isEqualTo(CREDENTIAL_ID);
                    assertThat(conflict.currentStatus()).isEqualTo(AccessCredentialStatus.REVOKED);
                    assertThat(conflict.requestedStatus()).isEqualTo(AccessCredentialStatus.REVOKED);
                });
    }

    @Test
    void policyRejectsMissingIdentifiersAndStatuses() {
        assertThatThrownBy(() -> AccessCredentialPolicy.requireIssueAllowed(null, ClientStatus.ACTIVE))
                .isInstanceOf(AccessCredentialValidationException.class);
        assertThatThrownBy(() -> AccessCredentialPolicy.requireIssueAllowed(CLIENT_ID, null))
                .isInstanceOf(AccessCredentialValidationException.class);
        assertThatThrownBy(() -> AccessCredentialPolicy.requireRevocationAllowed(null, null))
                .isInstanceOf(AccessCredentialValidationException.class);
    }
}
