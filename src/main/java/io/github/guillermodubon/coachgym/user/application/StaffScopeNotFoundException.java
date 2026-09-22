package io.github.guillermodubon.coachgym.user.application;

/** Indicates that an active staff scope does not exist. */
public class StaffScopeNotFoundException extends RuntimeException {

    public StaffScopeNotFoundException() {
        super("The staff scope was not found.");
    }
}
