package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.github.guillermodubon.coachgym.user.StaffPasswordChanged;
import io.github.guillermodubon.coachgym.user.StaffProfilePhotoChanged;
import io.github.guillermodubon.coachgym.user.StaffProfileUpdated;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffSelfProfileAuditEventListenerTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000007101");
    private static final Instant NOW = Instant.parse("2026-09-19T12:00:00Z");

    @Test
    void forwardsProfileUpdatesThroughTheExistingAuditPort() {
        AuditEntryStore store = mock(AuditEntryStore.class);
        StaffSelfProfileAuditEventListener listener =
                new StaffSelfProfileAuditEventListener(store);
        StaffProfileUpdated event = new StaffProfileUpdated(
                USER_ID, Set.of("firstName", "lastName"), NOW, "receptionist");

        listener.record(event);

        verify(store).recordStaffProfileUpdated(event);
        verifyNoMoreInteractions(store);
    }

    @Test
    void forwardsPhotoPresenceTransitionsWithoutPhotoContent() {
        AuditEntryStore store = mock(AuditEntryStore.class);
        StaffSelfProfileAuditEventListener listener =
                new StaffSelfProfileAuditEventListener(store);
        StaffProfilePhotoChanged event = new StaffProfilePhotoChanged(
                USER_ID, true, NOW, "receptionist");

        listener.record(event);

        verify(store).recordStaffProfilePhotoChanged(event);
        verifyNoMoreInteractions(store);
    }

    @Test
    void forwardsPasswordChangesWithoutCredentialValues() {
        AuditEntryStore store = mock(AuditEntryStore.class);
        StaffSelfProfileAuditEventListener listener =
                new StaffSelfProfileAuditEventListener(store);
        StaffPasswordChanged event = new StaffPasswordChanged(
                USER_ID, true, NOW, "receptionist");

        listener.record(event);

        verify(store).recordStaffPasswordChanged(event);
        verifyNoMoreInteractions(store);
    }
}
