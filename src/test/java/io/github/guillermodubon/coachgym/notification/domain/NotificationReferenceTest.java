package io.github.guillermodubon.coachgym.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.notification.NotificationResourceType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationReferenceTest {

    @Test
    void supportsAbsentAndCompleteReferences() {
        assertThat(NotificationReference.none().present()).isFalse();
        NotificationReference reference = new NotificationReference(
                NotificationResourceType.INCIDENT, UUID.randomUUID());
        assertThat(reference.present()).isTrue();
    }

    @Test
    void rejectsPartialReferencePairs() {
        assertThatThrownBy(() -> new NotificationReference(
                NotificationResourceType.MAINTENANCE, null))
                .isInstanceOf(NotificationValidationException.class);
        assertThatThrownBy(() -> new NotificationReference(
                null, UUID.randomUUID()))
                .isInstanceOf(NotificationValidationException.class);
    }
}
