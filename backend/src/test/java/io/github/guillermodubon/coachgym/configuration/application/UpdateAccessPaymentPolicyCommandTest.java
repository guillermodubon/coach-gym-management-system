package io.github.guillermodubon.coachgym.configuration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.configuration.AccessPaymentPolicyValidationException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class UpdateAccessPaymentPolicyCommandTest {

    @Test
    void commandContainsOnlyPolicyValueAndExpectedVersion() {
        UpdateAccessPaymentPolicyCommand command =
                new UpdateAccessPaymentPolicyCommand(true, 0L);

        assertThat(UpdateAccessPaymentPolicyCommand.class.isRecord()).isTrue();
        assertThat(command.requireConfirmedPaymentForAccess()).isTrue();
        assertThat(command.expectedVersion()).isZero();
        String components = Arrays.stream(
                        UpdateAccessPaymentPolicyCommand.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.joining(" "));
        assertThat(components).doesNotContain("actor", "timestamp", "paymentid");
    }

    @Test
    void commandRejectsNegativeExpectedVersion() {
        assertThatThrownBy(() ->
                new UpdateAccessPaymentPolicyCommand(false, -1L))
                .isInstanceOf(AccessPaymentPolicyValidationException.class)
                .hasMessage(
                        "Expected access payment policy version must not be negative.");
    }
}
