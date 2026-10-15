package io.github.guillermodubon.coachgym.membership.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.membership.application.ReactivateMembershipCommand;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReactivateMembershipRequestTest {

    @Test
    void shouldConvertRequestToCommand() {
        LocalDate reactivatedOn =
                LocalDate.of(
                        2026,
                        9,
                        15);

        ReactivateMembershipRequest request =
                new ReactivateMembershipRequest(
                        reactivatedOn,
                        3L);

        ReactivateMembershipCommand command =
                request.toCommand();

        assertThat(command.reactivatedOn())
                .isEqualTo(reactivatedOn);

        assertThat(command.version())
                .isEqualTo(3L);
    }
}
