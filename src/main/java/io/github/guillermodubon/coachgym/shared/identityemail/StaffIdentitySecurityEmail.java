package io.github.guillermodubon.coachgym.shared.identityemail;

import java.time.Instant;
import java.util.Objects;

/** Ephemeral provider-neutral security notice; never contains credentials or the administrator's reason. */
public record StaffIdentitySecurityEmail(
        String recipient,
        String displayName,
        IdentitySecurityNoticeType noticeType,
        String roleLabel,
        String scopeLabel,
        Instant occurredAt) {

    public StaffIdentitySecurityEmail {
        if (recipient == null || recipient.isBlank() || recipient.length() > 254
                || displayName == null || displayName.isBlank() || displayName.length() > 201) {
            throw new IllegalArgumentException("Identity security email recipient is invalid.");
        }
        noticeType = Objects.requireNonNull(noticeType);
        roleLabel = roleLabel == null ? "" : roleLabel;
        scopeLabel = scopeLabel == null ? "" : scopeLabel;
        if (noticeType == IdentitySecurityNoticeType.AUTHORITY_CHANGED
                && (roleLabel.isBlank() || scopeLabel.isBlank())) {
            throw new IllegalArgumentException("Authority notice labels are required.");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Identity security email time is required.");
        }
    }

    @Override
    public String toString() {
        return "StaffIdentitySecurityEmail[recipientPresent=" + !recipient.isBlank()
                + ", displayNamePresent=" + !displayName.isBlank()
                + ", noticeType=" + noticeType
                + ", roleLabelPresent=" + !roleLabel.isBlank()
                + ", scopeLabelPresent=" + !scopeLabel.isBlank()
                + ", occurredAt=" + occurredAt
                + ']';
    }
}
