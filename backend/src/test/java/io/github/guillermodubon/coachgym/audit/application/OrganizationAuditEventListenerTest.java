package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.organization.GymBranchCreated;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.github.guillermodubon.coachgym.organization.GymBranchStatusChanged;
import io.github.guillermodubon.coachgym.organization.GymBranchUpdated;
import io.github.guillermodubon.coachgym.organization.OrganizationUpdated;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrganizationAuditEventListenerTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-20T16:00:00Z");

    @Test
    void forwardsEachOrganizationLifecycleEventExactlyOnce() {
        AuditEntryStore store = Mockito.mock(AuditEntryStore.class);
        OrganizationAuditEventListener listener = new OrganizationAuditEventListener(store);

        OrganizationUpdated organizationUpdated = new OrganizationUpdated(
                ORGANIZATION_ID, "COACH_GYM", Set.of("brandName"),
                ACTOR_ID, "admin", OCCURRED_AT);
        GymBranchCreated branchCreated = new GymBranchCreated(
                BRANCH_ID, ORGANIZATION_ID, "NORTH", ACTOR_ID, "admin", OCCURRED_AT);
        GymBranchUpdated branchUpdated = new GymBranchUpdated(
                BRANCH_ID, ORGANIZATION_ID, "NORTH", Set.of("name"),
                ACTOR_ID, "admin", OCCURRED_AT);
        GymBranchStatusChanged branchStatusChanged = new GymBranchStatusChanged(
                BRANCH_ID, ORGANIZATION_ID, "NORTH", GymBranchStatus.ACTIVE,
                GymBranchStatus.INACTIVE, ACTOR_ID, "admin", OCCURRED_AT);

        listener.record(organizationUpdated);
        listener.record(branchCreated);
        listener.record(branchUpdated);
        listener.record(branchStatusChanged);

        verify(store, times(1)).recordOrganizationUpdated(organizationUpdated);
        verify(store, times(1)).recordGymBranchCreated(branchCreated);
        verify(store, times(1)).recordGymBranchUpdated(branchUpdated);
        verify(store, times(1)).recordGymBranchStatusChanged(branchStatusChanged);
    }
}
