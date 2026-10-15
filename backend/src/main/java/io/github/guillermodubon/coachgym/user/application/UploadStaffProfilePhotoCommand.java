package io.github.guillermodubon.coachgym.user.application;

/** Validated photo replacement command with an optimistic profile-version guard. */
public record UploadStaffProfilePhotoCommand(
        StaffProfilePhotoContent content,
        long expectedProfileVersion) {

    public UploadStaffProfilePhotoCommand {
        if (content == null) {
            throw new StaffProfileValidationException(
                    "Staff profile photo content is required.");
        }
        if (expectedProfileVersion < 0) {
            throw new StaffProfileValidationException(
                    "Expected profile version must not be negative.");
        }
    }
}
