package io.github.guillermodubon.coachgym.client.application;

import java.time.LocalDate;

public record RegisterClientCommand(
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        EmergencyContactCommand emergencyContact) {
}
