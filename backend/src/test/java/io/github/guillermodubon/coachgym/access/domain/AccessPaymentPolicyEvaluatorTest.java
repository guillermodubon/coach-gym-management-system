package io.github.guillermodubon.coachgym.access.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.access.AccessReasonCode;
import io.github.guillermodubon.coachgym.access.AccessResult;
import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicy;
import org.junit.jupiter.api.Test;

class AccessPaymentPolicyEvaluatorTest {

    @Test
    void disabledPolicyPreservesAnOtherwiseAllowedEvaluation() {
        AccessEvaluation base = AccessEvaluation.allowed();

        AccessEvaluation result = AccessPaymentPolicyEvaluator.evaluate(
                base, AccessPaymentPolicy.disabled(), false);

        assertThat(result).isSameAs(base);
    }

    @Test
    void enabledPolicyDeniesOnlyAnOtherwiseEligibleRequestWithoutPayment() {
        AccessEvaluation result = AccessPaymentPolicyEvaluator.evaluate(
                AccessEvaluation.allowed(), AccessPaymentPolicy.enabled(), false);

        assertThat(result.result()).isEqualTo(AccessResult.DENIED);
        assertThat(result.reasonCode()).isEqualTo(AccessReasonCode.PAYMENT_REQUIRED);
        assertThat(result.reason()).isEqualTo(
                "A confirmed payment is required for access.");
    }

    @Test
    void enabledPolicyPreservesAnOtherwiseAllowedEvaluationWithPayment() {
        AccessEvaluation base = AccessEvaluation.allowed();

        AccessEvaluation result = AccessPaymentPolicyEvaluator.evaluate(
                base, AccessPaymentPolicy.enabled(), true);

        assertThat(result).isSameAs(base);
    }

    @Test
    void earlierDenialAlwaysWinsAndDoesNotExposePaymentState() {
        AccessEvaluation base = AccessEvaluation.denied(
                AccessReasonCode.CLIENT_INACTIVE,
                "The client account is inactive.");

        AccessEvaluation result = AccessPaymentPolicyEvaluator.evaluate(
                base, AccessPaymentPolicy.enabled(), false);

        assertThat(result).isSameAs(base);
        assertThat(result.reasonCode()).isNotEqualTo(AccessReasonCode.PAYMENT_REQUIRED);
    }

    @Test
    void evaluatorRejectsMissingInputs() {
        assertThatThrownBy(() -> AccessPaymentPolicyEvaluator.evaluate(
                null, AccessPaymentPolicy.disabled(), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Base access evaluation must be provided.");

        assertThatThrownBy(() -> AccessPaymentPolicyEvaluator.evaluate(
                AccessEvaluation.allowed(), null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Access payment policy must be provided.");
    }
}
