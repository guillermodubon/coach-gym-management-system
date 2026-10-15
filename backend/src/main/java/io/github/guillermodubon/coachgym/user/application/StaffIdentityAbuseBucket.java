package io.github.guillermodubon.coachgym.user.application;

/** Allowlisted short-lived abuse counter partitions; never metric tag values. */
public enum StaffIdentityAbuseBucket {
    RECOVERY_EMAIL_REQUEST,
    RECOVERY_IP_REQUEST,
    RECOVERY_GLOBAL_REQUEST,
    INVITATION_TOKEN_FAILURE,
    INVITATION_IP_FAILURE,
    RECOVERY_TOKEN_FAILURE,
    RECOVERY_IP_FAILURE
}
