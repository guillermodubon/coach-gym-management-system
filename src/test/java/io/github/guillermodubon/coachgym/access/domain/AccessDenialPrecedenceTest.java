package io.github.guillermodubon.coachgym.access.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccessDenialPrecedenceTest {

    @Test
    void noDenialCandidateMeansAccessAllowed() {
        assertThat(AccessDenialPrecedence.selectHighestPriority(List.of()))
                .isEqualTo(AccessReasonCode.ACCESS_ALLOWED);
    }

    @Test
    void duplicateRetainsItsExistingPrecedenceOverMembershipAndPayment() {
        assertThat(AccessDenialPrecedence.selectHighestPriority(List.of(
                AccessReasonCode.PAYMENT_REQUIRED,
                AccessReasonCode.MEMBERSHIP_NOT_FOUND,
                AccessReasonCode.DUPLICATE_CHECK_IN)))
                .isEqualTo(AccessReasonCode.DUPLICATE_CHECK_IN);
    }

    @Test
    void existingMembershipLifecycleOrderIsPreserved() {
        assertThat(AccessDenialPrecedence.selectHighestPriority(List.of(
                AccessReasonCode.MEMBERSHIP_NOT_STARTED,
                AccessReasonCode.MEMBERSHIP_PERIOD_EXPIRED,
                AccessReasonCode.MEMBERSHIP_EXPIRED,
                AccessReasonCode.MEMBERSHIP_FROZEN,
                AccessReasonCode.MEMBERSHIP_CANCELLED)))
                .isEqualTo(AccessReasonCode.MEMBERSHIP_CANCELLED);
    }

    @Test
    void branchCoverageDenialFollowsLifecycleButPrecedesPayment() {
        assertThat(AccessDenialPrecedence.selectHighestPriority(List.of(
                AccessReasonCode.PAYMENT_REQUIRED,
                AccessReasonCode.MEMBERSHIP_NOT_VALID_AT_BRANCH)))
                .isEqualTo(AccessReasonCode.MEMBERSHIP_NOT_VALID_AT_BRANCH);
        assertThat(AccessDenialPrecedence.selectHighestPriority(List.of(
                AccessReasonCode.MEMBERSHIP_NOT_VALID_AT_BRANCH,
                AccessReasonCode.MEMBERSHIP_PERIOD_EXPIRED)))
                .isEqualTo(AccessReasonCode.MEMBERSHIP_PERIOD_EXPIRED);
    }

    @Test
    void credentialResolutionAndPrivacySafeIdentityFailuresRemainHighest() {
        assertThat(AccessDenialPrecedence.selectHighestPriority(List.of(
                AccessReasonCode.ACCESS_CREDENTIAL_INVALID,
                AccessReasonCode.DUPLICATE_CHECK_IN,
                AccessReasonCode.IDENTIFIER_NOT_FOUND)))
                .isEqualTo(AccessReasonCode.ACCESS_CREDENTIAL_INVALID);
        assertThat(AccessDenialPrecedence.selectHighestPriority(List.of(
                AccessReasonCode.MEMBERSHIP_NOT_FOUND,
                AccessReasonCode.CLIENT_INACTIVE,
                AccessReasonCode.IDENTIFIER_NOT_FOUND)))
                .isEqualTo(AccessReasonCode.IDENTIFIER_NOT_FOUND);
    }

    @Test
    void rejectsNullOrAllowedCandidates() {
        assertThatThrownBy(() ->
                AccessDenialPrecedence.selectHighestPriority(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                AccessDenialPrecedence.selectHighestPriority(
                        java.util.Arrays.asList(AccessReasonCode.CLIENT_INACTIVE, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                AccessDenialPrecedence.selectHighestPriority(
                        List.of(AccessReasonCode.ACCESS_ALLOWED)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
