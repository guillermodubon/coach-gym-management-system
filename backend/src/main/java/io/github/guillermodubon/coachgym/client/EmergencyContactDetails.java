package io.github.guillermodubon.coachgym.client;

import java.util.UUID;

public record EmergencyContactDetails(
        UUID id,
        String fullName,
        String relationship,
        String phone,
        boolean primary) {
}
