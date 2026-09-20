package io.github.guillermodubon.coachgym.user.application;

/** Raised when a profile-photo payload exceeds the approved byte limit. */
public final class StaffProfilePhotoTooLargeException
        extends StaffProfileValidationException {

    public StaffProfilePhotoTooLargeException() {
        super("Staff profile photo content exceeds the allowed size.");
    }
}
