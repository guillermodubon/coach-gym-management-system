package io.github.guillermodubon.coachgym.client;

import java.time.LocalDate;
import java.util.UUID;

/** Current membership snapshot displayed in the client profile. */
public record ClientMembershipSummary(
        UUID membershipId,
        String membershipCode,
        String status,
        String planName,
        LocalDate startsOn,
        LocalDate effectiveEndsOn) {

    public ClientMembershipSummary {
        if (membershipId == null) {
            throw new IllegalArgumentException("Membership id is required.");
        }
        membershipCode = required(membershipCode, "Membership code");
        status = required(status, "Membership status");
        planName = required(planName, "Membership plan name");
        if (startsOn == null || effectiveEndsOn == null) {
            throw new IllegalArgumentException(
                    "Membership period dates are required.");
        }
        if (startsOn.isAfter(effectiveEndsOn)) {
            throw new IllegalArgumentException(
                    "Membership start date must not be after its effective end date.");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.strip();
    }
}
