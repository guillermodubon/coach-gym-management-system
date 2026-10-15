package io.github.guillermodubon.coachgym.client.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.client.ClientStatus;
import io.github.guillermodubon.coachgym.client.application.ClientStateConflictException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientLifecyclePolicyTest {

    @Test
    void allowsOnlyActiveToInactiveAndInactiveToActive() {
        UUID clientId = UUID.randomUUID();

        assertThatCode(() -> ClientLifecyclePolicy.requireDeactivationAllowed(
                clientId, ClientStatus.ACTIVE)).doesNotThrowAnyException();
        assertThatCode(() -> ClientLifecyclePolicy.requireReactivationAllowed(
                clientId, ClientStatus.INACTIVE)).doesNotThrowAnyException();
    }

    @Test
    void rejectsIdempotentOrIncompatibleTransitions() {
        UUID clientId = UUID.randomUUID();

        assertThatThrownBy(() -> ClientLifecyclePolicy.requireDeactivationAllowed(
                clientId, ClientStatus.INACTIVE))
                .isInstanceOf(ClientStateConflictException.class)
                .satisfies(error -> {
                    ClientStateConflictException exception =
                            (ClientStateConflictException) error;
                    assertThat(exception.clientId()).isEqualTo(clientId);
                    assertThat(exception.currentStatus()).isEqualTo(ClientStatus.INACTIVE);
                    assertThat(exception.requestedStatus()).isEqualTo(ClientStatus.INACTIVE);
                });

        assertThatThrownBy(() -> ClientLifecyclePolicy.requireReactivationAllowed(
                clientId, ClientStatus.ACTIVE))
                .isInstanceOf(ClientStateConflictException.class);
    }
}
