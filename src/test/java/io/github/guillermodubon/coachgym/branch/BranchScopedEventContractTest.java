package io.github.guillermodubon.coachgym.branch;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.access.AccessAttemptRecorded;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialIssued;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialReplaced;
import io.github.guillermodubon.coachgym.accesscredential.AccessCredentialRevoked;
import io.github.guillermodubon.coachgym.client.ClientRegistered;
import io.github.guillermodubon.coachgym.equipment.EquipmentRegisteredEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentStatusChangedEvent;
import io.github.guillermodubon.coachgym.equipment.EquipmentUpdatedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentInvestigationStartedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentPriorityChangedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentReportedEvent;
import io.github.guillermodubon.coachgym.maintenance.IncidentResolvedEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceCancelledEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceCompletedEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceScheduledEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceStartedEvent;
import io.github.guillermodubon.coachgym.maintenance.MaintenanceUpdatedEvent;
import io.github.guillermodubon.coachgym.membership.MembershipCancelled;
import io.github.guillermodubon.coachgym.membership.MembershipCreated;
import io.github.guillermodubon.coachgym.membership.MembershipFrozen;
import io.github.guillermodubon.coachgym.membership.MembershipReactivated;
import io.github.guillermodubon.coachgym.membership.MembershipRenewed;
import io.github.guillermodubon.coachgym.notification.EmailDeliveryLifecycleEvent;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptCreated;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptProviderStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentAttemptStatusChanged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderEventAcknowledged;
import io.github.guillermodubon.coachgym.payment.PaymentProviderPaymentConfirmed;
import io.github.guillermodubon.coachgym.payment.PaymentReceiptGenerated;
import io.github.guillermodubon.coachgym.payment.PaymentRefunded;
import io.github.guillermodubon.coachgym.payment.PaymentRegistered;
import io.github.guillermodubon.coachgym.payment.PaymentVoided;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BranchScopedEventContractTest {

    @Test
    void branchOwnedPublicEventsUseOnePersistedUuidBranchComponent() {
        List<Class<?>> eventTypes = List.of(
                AccessAttemptRecorded.class,
                AccessCredentialIssued.class,
                AccessCredentialRevoked.class,
                AccessCredentialReplaced.class,
                ClientRegistered.class,
                MembershipCreated.class,
                MembershipRenewed.class,
                MembershipFrozen.class,
                MembershipReactivated.class,
                MembershipCancelled.class,
                PaymentRegistered.class,
                PaymentVoided.class,
                PaymentRefunded.class,
                PaymentAttemptCreated.class,
                PaymentAttemptStatusChanged.class,
                PaymentAttemptProviderStatusChanged.class,
                PaymentProviderEventAcknowledged.class,
                PaymentProviderPaymentConfirmed.class,
                PaymentReceiptGenerated.class,
                EquipmentRegisteredEvent.class,
                EquipmentUpdatedEvent.class,
                EquipmentStatusChangedEvent.class,
                IncidentReportedEvent.class,
                IncidentInvestigationStartedEvent.class,
                IncidentPriorityChangedEvent.class,
                IncidentResolvedEvent.class,
                MaintenanceScheduledEvent.class,
                MaintenanceUpdatedEvent.class,
                MaintenanceStartedEvent.class,
                MaintenanceCompletedEvent.class,
                MaintenanceCancelledEvent.class,
                EmailDeliveryLifecycleEvent.class);

        for (Class<?> eventType : eventTypes) {
            var branchComponents = java.util.Arrays.stream(eventType.getRecordComponents())
                    .filter(component -> component.getName().equals("branchId"))
                    .toList();
            assertThat(branchComponents)
                    .as("%s has a canonical branch snapshot", eventType.getSimpleName())
                    .hasSize(1);
            assertThat(branchComponents.getFirst().getType())
                    .as("%s branch snapshot type", eventType.getSimpleName())
                    .isEqualTo(UUID.class);
        }
    }
}
