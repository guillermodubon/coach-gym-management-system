package io.github.guillermodubon.coachgym.configuration.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyValidationException;
import org.junit.jupiter.api.Test;

class UpdateAccessPaymentPolicyRequestTest {

    @Test
    void mapsOnlyTheApprovedPolicyFields() {
        var request = new UpdateAccessPaymentPolicyRequest(true, 3L);

        assertThat(request.toCommand().requireConfirmedPaymentForAccess()).isTrue();
        assertThat(request.toCommand().expectedVersion()).isEqualTo(3L);
        assertThat(UpdateAccessPaymentPolicyRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("requireConfirmedPaymentForAccess", "version");
    }

    @Test
    void rejectsMissingOrNegativeValues() {
        assertThatThrownBy(() -> new UpdateAccessPaymentPolicyRequest(null, 0L).toCommand())
                .isInstanceOf(AccessPaymentPolicyValidationException.class);
        assertThatThrownBy(() -> new UpdateAccessPaymentPolicyRequest(false, null).toCommand())
                .isInstanceOf(AccessPaymentPolicyValidationException.class);
        assertThatThrownBy(() -> new UpdateAccessPaymentPolicyRequest(false, -1L).toCommand())
                .isInstanceOf(AccessPaymentPolicyValidationException.class);
    }
}
