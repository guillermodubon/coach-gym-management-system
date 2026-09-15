package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.maintenance.MaintenanceCancelledEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceCompletedEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceScheduledEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStartedEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenanceAuditEventListenerTest {

    @Mock private AuditEntryStore store;
    @Mock private MaintenanceScheduledEvent scheduled;
    @Mock private MaintenanceUpdatedEvent updated;
    @Mock private MaintenanceStartedEvent started;
    @Mock private MaintenanceCompletedEvent completed;
    @Mock private MaintenanceCancelledEvent cancelled;

    @Test
    void delegatesEveryMaintenanceLifecycleEvent() {
        MaintenanceAuditEventListener listener =
                new MaintenanceAuditEventListener(store);

        listener.on(scheduled);
        listener.on(updated);
        listener.on(started);
        listener.on(completed);
        listener.on(cancelled);

        verify(store).recordMaintenanceScheduled(scheduled);
        verify(store).recordMaintenanceUpdated(updated);
        verify(store).recordMaintenanceStarted(started);
        verify(store).recordMaintenanceCompleted(completed);
        verify(store).recordMaintenanceCancelled(cancelled);
    }
}
