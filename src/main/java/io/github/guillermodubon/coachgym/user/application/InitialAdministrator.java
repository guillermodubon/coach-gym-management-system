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

    @Override
    public String toString() {
        return "InitialAdministrator[usernamePresent=" + (username != null && !username.isBlank())
                + ", emailPresent=" + (email != null && !email.isBlank())
                + ", encodedPasswordPresent=" + (encodedPassword != null && !encodedPassword.isBlank())
                + ", firstNamePresent=" + (firstName != null && !firstName.isBlank())
                + ", lastNamePresent=" + (lastName != null && !lastName.isBlank())
                + ']';
    }
}
