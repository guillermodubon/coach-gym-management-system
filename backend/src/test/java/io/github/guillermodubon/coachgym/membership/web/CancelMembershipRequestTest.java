package io.github.guillermodubon.coachgym.membership.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.membership.application.CancelMembershipCommand;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CancelMembershipRequestTest {

    private static final LocalDate CANCELLED_ON =
            LocalDate.of(
                    2026,
                    9,
                    15);

    @Test
    void shouldConvertRequestToCommand() {
        CancelMembershipRequest request =
                new CancelMembershipRequest(
                        CANCELLED_ON,
                        "  Client requested cancellation  ",
                        2L);

        CancelMembershipCommand command =
                request.toCommand();

        assertThat(command.cancelledOn())
                .isEqualTo(CANCELLED_ON);

        assertThat(command.reason())
                .isEqualTo(
                        "Client requested cancellation");

        assertThat(command.version())
                .isEqualTo(2L);
    }

    @Test
    void shouldPreserveCancellationVersion() {
        CancelMembershipRequest request =
                new CancelMembershipRequest(
                        CANCELLED_ON,
                        "Client requested cancellation",
                        7L);

        assertThat(
                request.toCommand()
                        .version())
                .isEqualTo(7L);
    }
}
