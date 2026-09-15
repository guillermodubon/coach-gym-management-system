package io.github.guillermodubon.coachgym.notification.domain;

/** Validated and normalized human-readable notification content. */
public record NotificationContent(String title, String body) {

    public static final int MAX_TITLE_LENGTH = 160;
    public static final int MAX_BODY_LENGTH = 2_000;

    public NotificationContent {
        title = requiredText(title, "Notification title");
        body = requiredText(body, "Notification body");

        if (title.length() > MAX_TITLE_LENGTH) {
            throw new NotificationValidationException(
                    "Notification title must not exceed 160 characters.");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new NotificationValidationException(
                    "Notification body must not exceed 2000 characters.");
        }
    }

    private static String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new NotificationValidationException(label + " is required.");
        }
        return value.strip();
    }
}
