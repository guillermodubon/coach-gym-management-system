package io.github.guillermodubon.coachgym.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessPaymentPolicyTest {

    @Test
    void policyIsAnImmutableTypedValueWithBackwardCompatibleFactories() {
        assertThat(AccessPaymentPolicy.class.isRecord()).isTrue();
        assertThat(AccessPaymentPolicy.DEFAULT_REQUIRE_CONFIRMED_PAYMENT_FOR_ACCESS)
                .isFalse();
        assertThat(AccessPaymentPolicy.disabled().requireConfirmedPaymentForAccess())
                .isFalse();
        assertThat(AccessPaymentPolicy.enabled().requireConfirmedPaymentForAccess())
                .isTrue();
    }

    @Test
    void detailsRemainImmutableAndProjectTheirPolicyValue() {
        UUID actorId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        Instant updatedAt = Instant.parse("2026-09-14T12:00:00Z");

        AccessPaymentPolicyDetails details = new AccessPaymentPolicyDetails(
                true, 4L, updatedAt, actorId);

        assertThat(AccessPaymentPolicyDetails.class.isRecord()).isTrue();
        assertThat(details.policy()).isEqualTo(AccessPaymentPolicy.enabled());
        assertThat(details.version()).isEqualTo(4L);
        assertThat(details.updatedAt()).isEqualTo(updatedAt);
        assertThat(details.updatedByUserId()).isEqualTo(actorId);
    }

    @Test
    void detailsRejectNegativeVersions() {
        assertThatThrownBy(() -> new AccessPaymentPolicyDetails(false, -1L))
                .isInstanceOf(AccessPaymentPolicyValidationException.class)
                .hasMessage("Access payment policy version must not be negative.");
    }
}
