package io.github.guillermodubon.coachgym.accesscredential.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessCredentialCommandTest {

    private static final UUID CLIENT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000811");
    private static final UUID CREDENTIAL_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000812");

    @Test
    void commandsNormalizeReasonsAndPreserveExpectedVersion() {
        IssueAccessCredentialCommand issue = new IssueAccessCredentialCommand(CLIENT_ID);
        RevokeAccessCredentialCommand revoke = new RevokeAccessCredentialCommand(
                CREDENTIAL_ID, " Lost credential ", 2);
        ReplaceAccessCredentialCommand replace = new ReplaceAccessCredentialCommand(
                CREDENTIAL_ID, " Security review ", 3);

        assertThat(issue.clientId()).isEqualTo(CLIENT_ID);
        assertThat(revoke.reason()).isEqualTo("Lost credential");
        assertThat(revoke.expectedVersion()).isEqualTo(2);
        assertThat(replace.reason()).isEqualTo("Security review");
        assertThat(replace.expectedVersion()).isEqualTo(3);
    }

    @Test
    void commandsRejectMissingIdentifiersBlankReasonsAndNegativeVersions() {
        assertThatThrownBy(() -> new IssueAccessCredentialCommand(null))
                .isInstanceOf(AccessCredentialValidationException.class);
        assertThatThrownBy(() -> new RevokeAccessCredentialCommand(
                CREDENTIAL_ID, " ", 0))
                .isInstanceOf(AccessCredentialValidationException.class);
        assertThatThrownBy(() -> new ReplaceAccessCredentialCommand(
                CREDENTIAL_ID, "Valid reason", -1))
                .isInstanceOf(AccessCredentialValidationException.class);
        assertThatThrownBy(() -> new RevokeAccessCredentialCommand(
                null, "Valid reason", 0))
                .isInstanceOf(AccessCredentialValidationException.class);
    }

    @Test
    void reasonsHaveExplicitLengthBounds() {
        assertThatThrownBy(() -> new RevokeAccessCredentialCommand(
                CREDENTIAL_ID, "No", 0))
                .isInstanceOf(AccessCredentialValidationException.class)
                .hasMessageContaining("at least 3");

        assertThatThrownBy(() -> new ReplaceAccessCredentialCommand(
                CREDENTIAL_ID, "x".repeat(2001), 0))
                .isInstanceOf(AccessCredentialValidationException.class)
                .hasMessageContaining("2000");
    }
}
