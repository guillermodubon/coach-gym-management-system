package io.github.guillermodubon.coachgym.user.application;

/**
 * Data required to create the first privileged staff account of an empty system.
 */
public record InitialAdministrator(
        String username,
        String email,
        String encodedPassword,
        String firstName,
        String lastName) {
}
