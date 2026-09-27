package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PasswordRecoveryPolicyTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-24T12:00:00Z");

    @Test
    void recoveryLifecycleAllowsOnlyOnePendingToTerminalTransition() {
        assertThat(PasswordRecoveryStatus.values()).containsExactly(
                PasswordRecoveryStatus.PENDING,
                PasswordRecoveryStatus.USED,
                PasswordRecoveryStatus.EXPIRED,
                PasswordRecoveryStatus.REVOKED);
        for (PasswordRecoveryStatus terminal : List.of(
                PasswordRecoveryStatus.USED,
                PasswordRecoveryStatus.EXPIRED,
                PasswordRecoveryStatus.REVOKED)) {
            assertThat(PasswordRecoveryLifecyclePolicy.allowsTransition(
                    PasswordRecoveryStatus.PENDING, terminal)).isTrue();
            assertThat(PasswordRecoveryStatus.values())
                    .as("terminal recovery state %s cannot transition", terminal)
                    .allSatisfy(next -> assertThat(
                            PasswordRecoveryLifecyclePolicy.allowsTransition(terminal, next)).isFalse());
        }
        assertThatThrownBy(() -> PasswordRecoveryLifecyclePolicy.requireTransition(
                PasswordRecoveryStatus.USED, PasswordRecoveryStatus.PENDING))
                .isInstanceOf(StaffIdentityStateConflictException.class);
    }

    @Test
    void recoveryExpirationMustBePositiveAndNoLongerThanThirtyMinutes() {
        assertThat(PasswordRecoveryPolicy.DEFAULT_LIFETIME).isEqualTo(Duration.ofMinutes(15));
        assertThat(PasswordRecoveryPolicy.requireExpiration(
                CREATED_AT, CREATED_AT.plus(Duration.ofMinutes(30))))
                .isEqualTo(CREATED_AT.plus(Duration.ofMinutes(30)));
        assertThatThrownBy(() -> PasswordRecoveryPolicy.requireExpiration(CREATED_AT, CREATED_AT))
                .isInstanceOf(StaffIdentityValidationException.class);
        assertThatThrownBy(() -> PasswordRecoveryPolicy.requireLifetime(Duration.ofMinutes(30).plusNanos(1)))
                .isInstanceOf(StaffIdentityValidationException.class);
    }

    @Test
    void requestAcknowledgementDoesNotRevealAccountExistenceOrEligibility() {
        assertThat(PasswordRecoveryPolicy.publicResponse(false))
                .isEqualTo(PasswordRecoveryPolicy.publicResponse(true))
                .isEqualTo(PasswordRecoveryPolicy.GENERIC_PUBLIC_RESPONSE)
                .doesNotContain("active", "inactive", "not found", "@");

        assertThat(PasswordRecoveryPolicy.mayIssueRecovery(StaffIdentityStatus.ACTIVE)).isTrue();
        assertThat(PasswordRecoveryPolicy.mayIssueRecovery(StaffIdentityStatus.SUSPENDED)).isFalse();
        assertThat(PasswordRecoveryPolicy.mayIssueRecovery(StaffIdentityStatus.DEACTIVATED)).isFalse();
        assertThat(PasswordRecoveryPolicy.mayIssueRecovery(StaffIdentityStatus.INVITED)).isFalse();
    }
}
