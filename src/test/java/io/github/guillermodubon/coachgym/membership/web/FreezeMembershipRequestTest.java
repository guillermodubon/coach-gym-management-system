package io.github.guillermodubon.coachgym.membership.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.membership.application.FreezeMembershipCommand;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FreezeMembershipRequestTest {

    private static final LocalDate STARTS_ON =
            LocalDate.of(
                    2026,
                    9,
                    10);

    private static final LocalDate PLANNED_ENDS_ON =
            LocalDate.of(
                    2026,
                    9,
                    20);

    @Test
    void shouldConvertRequestToCommand() {
        FreezeMembershipRequest request =
                new FreezeMembershipRequest(
                        STARTS_ON,
                        PLANNED_ENDS_ON,
                        "  Medical leave  ",
                        2L);

        FreezeMembershipCommand command =
                request.toCommand();

        assertThat(command.startsOn())
                .isEqualTo(STARTS_ON);

        assertThat(command.plannedEndsOn())
                .isEqualTo(PLANNED_ENDS_ON);

        assertThat(command.reason())
                .isEqualTo("Medical leave");

        assertThat(command.version())
                .isEqualTo(2L);
    }
}
