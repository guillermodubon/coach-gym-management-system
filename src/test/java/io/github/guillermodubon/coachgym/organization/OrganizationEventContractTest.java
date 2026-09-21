package io.github.guillermodubon.coachgym.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationEventContractTest {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-20T16:00:00Z");

    @Test
    void publicEventsExposeOnlyPrivacySafeMetadata() {
        assertThat(componentNames(OrganizationUpdated.class))
                .containsExactlyInAnyOrder(
                        "organizationId", "organizationCode", "changedFields",
                        "actorUserId", "actorIdentifier", "occurredAt");
        assertThat(componentNames(GymBranchCreated.class))
                .containsExactlyInAnyOrder(
                        "branchId", "organizationId", "branchCode",
                        "actorUserId", "actorIdentifier", "occurredAt");
        assertThat(componentNames(GymBranchUpdated.class))
                .containsExactlyInAnyOrder(
                        "branchId", "organizationId", "branchCode", "changedFields",
                        "actorUserId", "actorIdentifier", "occurredAt");
        assertThat(componentNames(GymBranchStatusChanged.class))
                .containsExactlyInAnyOrder(
                        "branchId", "organizationId", "branchCode", "previousStatus",
                        "newStatus", "actorUserId", "actorIdentifier", "occurredAt");
    }

    @Test
    void changedFieldSetsAreImmutableAndStatusEventsRequireARealTransition() {
        OrganizationUpdated event = new OrganizationUpdated(
                ORGANIZATION_ID,
                "COACH_GYM",
                Set.of("brandName"),
                ACTOR_ID,
                "admin",
                OCCURRED_AT);

        assertThat(event.changedFields()).containsExactly("brandName");
        assertThatThrownBy(() -> event.changedFields().add("supportEmail"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new GymBranchStatusChanged(
                BRANCH_ID,
                ORGANIZATION_ID,
                "NORTH",
                GymBranchStatus.ACTIVE,
                GymBranchStatus.ACTIVE,
                ACTOR_ID,
                "admin",
                OCCURRED_AT))
                .isInstanceOf(GymBranchValidationException.class);
    }

    private static Set<String> componentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
