package io.github.guillermodubon.coachgym.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentAttemptSearchQueryTest {

    @Test
    void requiresMembershipAndPeriodAndBoundsPageSize() {
        UUID membershipId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();

        assertThat(new PaymentAttemptSearchQuery(
                membershipId, periodId, null, null, 2, 25).offset()).isEqualTo(50);
        assertThatThrownBy(() -> new PaymentAttemptSearchQuery(
                null, periodId, null, null, 0, 25))
                .isInstanceOf(PaymentAttemptQueryValidationException.class);
        assertThatThrownBy(() -> new PaymentAttemptSearchQuery(
                membershipId, null, null, null, 0, 25))
                .isInstanceOf(PaymentAttemptQueryValidationException.class);
    }

    @Test
    void rejectsNegativePagesAndUnboundedPageSizes() {
        UUID membershipId = UUID.randomUUID();
        UUID periodId = UUID.randomUUID();

        assertThatThrownBy(() -> new PaymentAttemptSearchQuery(
                membershipId, periodId, null, null, -1, 25))
                .isInstanceOf(PaymentAttemptQueryValidationException.class);
        assertThatThrownBy(() -> new PaymentAttemptSearchQuery(
                membershipId, periodId, null, null, 0, 0))
                .isInstanceOf(PaymentAttemptQueryValidationException.class);
        assertThatThrownBy(() -> new PaymentAttemptSearchQuery(
                membershipId, periodId, null, null, 0, 101))
                .isInstanceOf(PaymentAttemptQueryValidationException.class);
    }
}
