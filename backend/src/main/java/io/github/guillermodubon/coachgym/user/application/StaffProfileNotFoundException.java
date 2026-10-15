package io.github.guillermodubon.coachgym.user.application;

/** Raised when an active authenticated staff profile cannot be found. */
public class StaffProfileNotFoundException extends RuntimeException {

    public StaffProfileNotFoundException() {
        super("Staff profile was not found.");
    }
}
