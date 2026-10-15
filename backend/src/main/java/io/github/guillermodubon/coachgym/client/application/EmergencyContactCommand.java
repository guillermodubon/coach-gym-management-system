package io.github.guillermodubon.coachgym.client.application;

public record EmergencyContactCommand(
        String fullName,
        String relationship,
        String phone) {
}
