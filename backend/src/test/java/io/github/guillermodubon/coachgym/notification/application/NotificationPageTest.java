package io.github.guillermodubon.coachgym.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.notification.domain.NotificationValidationException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationPageTest {

    @Test
    void createsImmutableEmptyPage() {
        NotificationPage page = new NotificationPage(List.of(), 0, 25, 0, 0);
        assertThat(page.items()).isEmpty();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void defensivelyCopiesItems() {
        ArrayList<io.github.guillermodubon.coachgym.notification.NotificationDetails> items =
                new ArrayList<>();
        NotificationPage page = new NotificationPage(items, 0, 25, 0, 0);
        items.clear();
        assertThat(page.items()).isEmpty();
        assertThatThrownBy(() -> page.items().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInconsistentPageMetadata() {
        assertThatThrownBy(() -> new NotificationPage(List.of(), 0, 25, 26, 1))
                .isInstanceOf(NotificationValidationException.class);
        assertThatThrownBy(() -> new NotificationPage(List.of(), 0, 0, 0, 0))
                .isInstanceOf(NotificationValidationException.class);
    }
}
