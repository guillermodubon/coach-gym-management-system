package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class StaffSelfProfileEventContractTest {

    @Test
    void eventsContainOnlyPrivacySafeChangeMetadata() {
        for (Class<?> eventType : Set.of(
                StaffProfileUpdated.class,
                StaffProfilePhotoChanged.class,
                StaffPasswordChanged.class)) {
            Set<String> components = Arrays.stream(eventType.getRecordComponents())
                    .map(RecordComponent::getName)
                    .collect(Collectors.toSet());
            assertThat(components)
                    .doesNotContain(
                            "password",
                            "passwordHash",
                            "currentPassword",
                            "newPassword",
                            "storageKey",
                            "checksumSha256",
                            "photoBytes",
                            "sessionId",
                            "email",
                            "phone");
        }
    }

    @Test
    void profileEventCopiesChangedFieldsAndRejectsUnsupportedFields() {
        Set<String> fields = new java.util.HashSet<>(Set.of("firstName"));
        StaffProfileUpdated event = new StaffProfileUpdated(
                UUID.randomUUID(), fields, Instant.parse("2026-09-19T12:00:00Z"));
        fields.add("lastName");

        assertThat(event.changedFields()).containsExactly("firstName");
        assertThat(event.toString()).doesNotContain("password", "storageKey");
    }
}
