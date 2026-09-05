package io.github.guillermodubon.coachgym.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NotificationContentTest {

    @Test
    void normalizesRequiredContent() {
        NotificationContent content = new NotificationContent(
                "  Maintenance assigned  ",
                "  Work order MNT-000001 was assigned.  ");
        assertThat(content.title()).isEqualTo("Maintenance assigned");
        assertThat(content.body())
                .isEqualTo("Work order MNT-000001 was assigned.");
    }

    @Test
    void rejectsBlankTitleAndBody() {
        assertThatThrownBy(() -> new NotificationContent(" ", "Body"))
                .isInstanceOf(NotificationValidationException.class);
        assertThatThrownBy(() -> new NotificationContent("Title", " "))
                .isInstanceOf(NotificationValidationException.class);
    }

    @Test
    void rejectsContentBeyondSupportedLimits() {
        assertThatThrownBy(() -> new NotificationContent("x".repeat(161), "Body"))
                .isInstanceOf(NotificationValidationException.class);
        assertThatThrownBy(() -> new NotificationContent("Title", "x".repeat(2001)))
                .isInstanceOf(NotificationValidationException.class);
    }
}
