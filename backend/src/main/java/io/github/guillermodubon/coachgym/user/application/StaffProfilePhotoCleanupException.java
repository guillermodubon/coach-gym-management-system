package io.github.guillermodubon.coachgym.user.application;

/** Indicates that committed photo metadata could not be followed by old-object cleanup. */
public class StaffProfilePhotoCleanupException extends StaffProfilePhotoStorageException {

    public StaffProfilePhotoCleanupException(Throwable cause) {
        super("Staff profile photo cleanup could not be completed.", cause);
    }
}
