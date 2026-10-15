package io.github.guillermodubon.coachgym.user;

/**
 * Lifecycle state of an internally provisioned staff identity.
 *
 * <p>{@link #INVITED} is an effective state backed only by a pending
 * invitation; the selected Block 1 policy does not create a placeholder user
 * row.</p>
 */
public enum StaffIdentityStatus {
    INVITED,
    ACTIVE,
    SUSPENDED,
    DEACTIVATED
}
