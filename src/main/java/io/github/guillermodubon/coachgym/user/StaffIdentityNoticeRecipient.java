package io.github.guillermodubon.coachgym.user;

/** Minimal private recipient projection available only to identity notification consumers. */
public record StaffIdentityNoticeRecipient(String email, String displayName) {

    public StaffIdentityNoticeRecipient {
        email = StaffIdentityValuePolicy.normalizeEmail(email);
        if (displayName == null || displayName.isBlank() || displayName.length() > 201
                || displayName.chars().anyMatch(Character::isISOControl)) {
            throw new StaffIdentityValidationException("Staff display name is invalid.");
        }
        displayName = displayName.strip().replaceAll("\\s+", " ");
    }

    @Override
    public String toString() {
        return "StaffIdentityNoticeRecipient[emailPresent=" + !email.isBlank()
                + ", displayNamePresent=" + !displayName.isBlank()
                + ']';
    }
}
