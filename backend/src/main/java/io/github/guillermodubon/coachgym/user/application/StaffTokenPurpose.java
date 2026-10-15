package io.github.guillermodubon.coachgym.user.application;

/** Domain separation and persisted scheme for each staff one-time token. */
public enum StaffTokenPurpose {
    INVITATION("staff-invitation-sha256-v1", "coach-gym:staff-invitation:v1:"),
    PASSWORD_RECOVERY("staff-password-recovery-sha256-v1", "coach-gym:staff-password-recovery:v1:");

    private final String schemeVersion;
    private final String domainSeparator;

    StaffTokenPurpose(String schemeVersion, String domainSeparator) {
        this.schemeVersion = schemeVersion;
        this.domainSeparator = domainSeparator;
    }

    public String schemeVersion() {
        return schemeVersion;
    }

    public String domainSeparator() {
        return domainSeparator;
    }
}
