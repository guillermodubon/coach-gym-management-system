package io.github.guillermodubon.coachgym.organization.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.guillermodubon.coachgym.organization.ChangeGymBranchStatusCommand;
import io.github.guillermodubon.coachgym.organization.CreateGymBranchCommand;
import io.github.guillermodubon.coachgym.organization.GymBranchCreated;
import io.github.guillermodubon.coachgym.organization.GymBranchDetails;
import io.github.guillermodubon.coachgym.organization.GymBranchStatus;
import io.github.guillermodubon.coachgym.organization.GymBranchStatusChanged;
import io.github.guillermodubon.coachgym.organization.GymBranchUpdated;
import io.github.guillermodubon.coachgym.organization.OrganizationDetails;
import io.github.guillermodubon.coachgym.organization.OrganizationStatus;
import io.github.guillermodubon.coachgym.organization.UpdateGymBranchCommand;
import io.github.guillermodubon.coachgym.user.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class GymBranchApplicationServiceTest {

    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000001");
    private static final UUID BRANCH_ID = UUID.fromString(
            "7b0bf7d5-5184-43d2-8f9a-200000000010");
    private static final UUID ACTOR_ID = UUID.fromString(
            "a31031de-d7c7-4887-bcae-749afea5b94c");
    private static final Instant NOW = Instant.parse("2026-09-20T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthenticatedActor ACTOR =
            new AuthenticatedActor(ACTOR_ID, "coach-admin");

    @Mock
    private GymBranchQuery branchQuery;

    @Mock
    private GymBranchStore branchStore;

    @Mock
    private OrganizationQuery organizationQuery;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private GymBranchApplicationService service;

    @BeforeEach
    void setUp() {
        service = new GymBranchApplicationService(
                branchQuery, branchStore, organizationQuery, eventPublisher, CLOCK);
    }

    @Test
    void createInheritsCanonicalTimezoneAndPublishesOneSafeEvent() {
        OrganizationDetails organization = organization();
        GymBranchDetails created = branch(false, "NORTH", "North Gym", 0);
        when(organizationQuery.findCanonical()).thenReturn(Optional.of(organization));
        when(branchStore.create(any(CreateGymBranchCommand.class), eq(NOW)))
                .thenReturn(created);

        GymBranchDetails result = service.create(commandWithoutTimezone(), ACTOR);

        assertThat(result).isEqualTo(created);
        ArgumentCaptor<CreateGymBranchCommand> command =
                ArgumentCaptor.forClass(CreateGymBranchCommand.class);
        verify(branchStore).create(command.capture(), eq(NOW));
        assertThat(command.getValue().timezone()).isEqualTo("America/El_Salvador");

        ArgumentCaptor<GymBranchCreated> event =
                ArgumentCaptor.forClass(GymBranchCreated.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().branchId()).isEqualTo(BRANCH_ID);
        assertThat(event.getValue().branchCode()).isEqualTo("NORTH");
        assertThat(event.getValue().actorUserId()).isEqualTo(ACTOR_ID);
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void noOpAndStaleUpdatesDoNotWriteOrPublish() {
        GymBranchDetails current = branch(false, "NORTH", "North Gym", 4);
        when(branchQuery.findById(BRANCH_ID)).thenReturn(Optional.of(current));
        UpdateGymBranchCommand same = updateCommand(current, 4);

        assertThat(service.update(BRANCH_ID, same, ACTOR)).isEqualTo(current);
        verify(branchStore, never()).update(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());

        assertThatThrownBy(() -> service.update(
                BRANCH_ID,
                new UpdateGymBranchCommand(
                        "Changed", current.addressLine1(), current.addressLine2(),
                        current.city(), current.stateOrDepartment(), current.postalCode(),
                        current.countryCode(), current.phone(), current.email(),
                        current.timezone(), 3),
                ACTOR))
                .isInstanceOf(GymBranchVersionConflictException.class);
        verify(branchStore, never()).update(any(), any(), any());
    }

    @Test
    void statusChangePublishesPreviousAndNewStatus() {
        GymBranchDetails current = branch(false, "NORTH", "North Gym", 2);
        GymBranchDetails updated = branch(false, "NORTH", "North Gym", 3, GymBranchStatus.INACTIVE);
        when(branchQuery.findById(BRANCH_ID)).thenReturn(Optional.of(current));
        when(branchStore.changeStatus(eq(BRANCH_ID), any(ChangeGymBranchStatusCommand.class), eq(NOW)))
                .thenReturn(updated);

        service.changeStatus(
                BRANCH_ID,
                new ChangeGymBranchStatusCommand(GymBranchStatus.INACTIVE, "Renovation", 2),
                ACTOR);

        ArgumentCaptor<GymBranchStatusChanged> event =
                ArgumentCaptor.forClass(GymBranchStatusChanged.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().previousStatus()).isEqualTo(GymBranchStatus.ACTIVE);
        assertThat(event.getValue().newStatus()).isEqualTo(GymBranchStatus.INACTIVE);
    }

    private static OrganizationDetails organization() {
        return new OrganizationDetails(
                ORGANIZATION_ID,
                "COACH_GYM",
                "Coach Gym",
                "Coach Gym",
                null,
                null,
                "America/El_Salvador",
                "USD",
                OrganizationStatus.ACTIVE,
                NOW.minusSeconds(3_600),
                NOW,
                0);
    }

    private static CreateGymBranchCommand commandWithoutTimezone() {
        return new CreateGymBranchCommand(
                "NORTH", "North Gym", "Main Street 1", null, "North City",
                null, null, "SV", "+50370000000", "branch@example.test", null);
    }

    private static UpdateGymBranchCommand updateCommand(
            GymBranchDetails current, long version) {
        return new UpdateGymBranchCommand(
                current.name(), current.addressLine1(), current.addressLine2(),
                current.city(), current.stateOrDepartment(), current.postalCode(),
                current.countryCode(), current.phone(), current.email(),
                current.timezone(), version);
    }

    private static GymBranchDetails branch(
            boolean initial, String code, String name, long version) {
        return branch(initial, code, name, version, GymBranchStatus.ACTIVE);
    }

    private static GymBranchDetails branch(
            boolean initial,
            String code,
            String name,
            long version,
            GymBranchStatus status) {
        return new GymBranchDetails(
                BRANCH_ID,
                ORGANIZATION_ID,
                code,
                name,
                "Main Street 1",
                null,
                "North City",
                null,
                null,
                "SV",
                "+50370000000",
                "branch@example.test",
                "America/El_Salvador",
                status,
                initial,
                NOW.minusSeconds(3_600),
                NOW,
                version);
    }
}
