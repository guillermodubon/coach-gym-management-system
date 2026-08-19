package io.github.guillermodubon.coachgym.membership.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.membership.application.RenewMembershipCommand;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenewMembershipRequestTest {

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "4f73cc29-e515-4c88-802f-c5ead831a364");

    private static final UUID PROMOTION_ID =
            UUID.fromString(
                    "d1521065-3edb-4d87-bbd3-dfc66fe7bee6");

    @Test
    void convertsRequestToCommand() {
        RenewMembershipRequest request =
                new RenewMembershipRequest(
                        PLAN_ID,
                        PROMOTION_ID,
                        LocalDate.of(2026, 10, 5),
                        2L);

        RenewMembershipCommand command =
                request.toCommand();

        assertThat(command.membershipPlanId())
                .isEqualTo(PLAN_ID);

        assertThat(command.promotionId())
                .isEqualTo(PROMOTION_ID);

        assertThat(command.startsOn())
                .isEqualTo(
                        LocalDate.of(2026, 10, 5));

        assertThat(command.version())
                .isEqualTo(2);
    }

    @Test
    void convertsActiveRenewalRequestWithoutStartDate() {
        RenewMembershipRequest request =
                new RenewMembershipRequest(
                        PLAN_ID,
                        null,
                        null,
                        0L);

        RenewMembershipCommand command =
                request.toCommand();

        assertThat(command.startsOn())
                .isNull();

        assertThat(command.promotionId())
                .isNull();

        assertThat(command.version())
                .isZero();
    }
}
