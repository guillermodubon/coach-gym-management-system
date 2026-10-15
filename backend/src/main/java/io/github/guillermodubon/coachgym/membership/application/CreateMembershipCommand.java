package io.github.guillermodubon.coachgym.membership.application;

import java.time.LocalDate;
import java.util.UUID;

public record CreateMembershipCommand(
        UUID clientId,
        UUID membershipPlanId,
        UUID promotionId,
        LocalDate startsOn) {
}