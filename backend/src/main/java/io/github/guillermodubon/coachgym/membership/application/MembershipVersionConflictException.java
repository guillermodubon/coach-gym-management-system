package io.github.guillermodubon.coachgym.membership.application;

import java.util.UUID;

public class MembershipVersionConflictException
        extends RuntimeException {

    private final UUID membershipId;
    private final long expectedVersion;
    private final long currentVersion;

    public MembershipVersionConflictException(
            UUID membershipId,
            long expectedVersion,
            long currentVersion) {

        super(
                "Membership "
                        + membershipId
                        + " was modified by another operation. "
                        + "Expected version "
                        + expectedVersion
                        + " but found "
                        + currentVersion
                        + ".");

        this.membershipId = membershipId;
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }

    public UUID membershipId() {
        return membershipId;
    }

    public long expectedVersion() {
        return expectedVersion;
    }

    public long currentVersion() {
        return currentVersion;
    }
}
