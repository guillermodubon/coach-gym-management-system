package io.github.guillermodubon.coachgym.client.web;

import io.github.guillermodubon.coachgym.client.EmergencyContactDetails;
import java.util.UUID;

public record EmergencyContactResponse(
        UUID id,
        String fullName,
        String relationship,
        String phone,
        boolean primary) {

    static EmergencyContactResponse from(EmergencyContactDetails contact) {
        return new EmergencyContactResponse(
                contact.id(),
                contact.fullName(),
                contact.relationship(),
                contact.phone(),
                contact.primary());
    }
}
