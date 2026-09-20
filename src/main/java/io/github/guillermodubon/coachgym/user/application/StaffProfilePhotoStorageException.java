package io.github.guillermodubon.coachgym.user.application;

/** Safe provider-neutral failure for staff profile photo storage operations. */
public class StaffProfilePhotoStorageException extends RuntimeException {

    public StaffProfilePhotoStorageException(String message) {
        super(message);
    }

    public StaffProfilePhotoStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
