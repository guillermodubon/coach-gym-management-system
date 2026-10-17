package io.github.guillermodubon.coachgym.user.application;

/** Raised when a staff profile has no stored photo metadata. */
public class StaffProfilePhotoNotFoundException extends RuntimeException {

    public StaffProfilePhotoNotFoundException() {
        super("Staff profile photo was not found.");
    }
}
