package io.github.guillermodubon.coachgym.audit.application;

import static org.mockito.Mockito.verify;

import io.github.guillermodubon.coachgym.membership.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipAuditEventListenerTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "e8ff4202-afdb-43f6-b511-44ac9037675d");

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "66dd795a-d7c6-4bce-8582-f80ac90dc0e1");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "8c92aee4-1fdc-4c33-9792-b6bcb4e03bf1");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "989c1919-b18a-4dd0-88f2-c28d35850640");

    private static final UUID PROMOTION_ID =
            UUID.fromString(
                    "b531f319-d6b9-4f87-8c5e-8efb328d62d9");

    private static final UUID ACTOR_ID =
            UUID.fromString(
                    "d58dcc34-f37a-4449-b8b4-1a46bb417ea7");

    private static final UUID FREEZE_ID =
            UUID.fromString(
                    "40000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse(
                    "2026-08-16T21:00:00Z");

    @Mock
    private AuditEntryStore auditEntryStore;

    private MembershipAuditEventListener listener;

    @BeforeEach
    void setUp() {
        listener =
                new MembershipAuditEventListener(
                        auditEntryStore);
    }

    @Test
    void forwardsMembershipCreatedEvent() {
        MembershipCreated event =
                membershipCreated(
                        PROMOTION_ID);

        listener.record(event);

        verify(auditEntryStore)
                .recordMembershipCreated(event);
    }

    @Test
    void forwardsMembershipRenewedEvent() {
        MembershipRenewed event =
                membershipRenewed(
                        PROMOTION_ID);

        listener.record(event);

        verify(auditEntryStore)
                .recordMembershipRenewed(event);
    }

    @Test
    void shouldForwardMembershipFrozenEvent() {
        MembershipFrozen event =
                new MembershipFrozen(
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        CLIENT_ID,
                        PERIOD_ID,
                        LocalDate.of(
                                2026,
                                9,
                                10),
                        LocalDate.of(
                                2026,
                                9,
                                20),
                        "Medical leave",
                        MembershipStatus.ACTIVE,
                        MembershipStatus.FROZEN,
                        ACTOR_ID,
                        "coach-admin",
                        NOW);

        listener.record(event);

        verify(auditEntryStore)
                .recordMembershipFrozen(event);
    }

    @Test
    void shouldForwardMembershipReactivatedEvent() {
        MembershipReactivated event =
                new MembershipReactivated(
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        CLIENT_ID,
                        PERIOD_ID,
                        FREEZE_ID,
                        LocalDate.of(
                                2026,
                                9,
                                10),
                        LocalDate.of(
                                2026,
                                9,
                                20),
                        LocalDate.of(
                                2026,
                                9,
                                15),
                        "Medical leave",
                        MembershipStatus.FROZEN,
                        MembershipStatus.ACTIVE,
                        ACTOR_ID,
                        "coach-admin",
                        NOW);

        listener.record(event);

        verify(auditEntryStore)
                .recordMembershipReactivated(event);
    }

    private static MembershipCreated membershipCreated(
            UUID promotionId) {

        return new MembershipCreated(
                MEMBERSHIP_ID,
                "MEM-000001",
                CLIENT_ID,
                PERIOD_ID,
                PLAN_ID,
                promotionId,
                new BigDecimal("25.00"),
                new BigDecimal("2.50"),
                new BigDecimal("22.50"),
                "USD",
                LocalDate.of(
                        2026,
                        9,
                        1),
                LocalDate.of(
                        2026,
                        10,
                        1),
                ACTOR_ID,
                "coach-admin",
                NOW);
    }

    private static MembershipRenewed membershipRenewed(
            UUID promotionId) {

        return new MembershipRenewed(
                MEMBERSHIP_ID,
                "MEM-000001",
                CLIENT_ID,
                PERIOD_ID,
                (short) 2,
                PLAN_ID,
                promotionId,
                new BigDecimal("25.00"),
                new BigDecimal("2.50"),
                new BigDecimal("22.50"),
                "USD",
                LocalDate.of(
                        2026,
                        10,
                        1),
                LocalDate.of(
                        2026,
                        11,
                        1),
                MembershipStatus.ACTIVE,
                MembershipStatus.ACTIVE,
                ACTOR_ID,
                "coach-admin",
                NOW);
    }

    @Test
    void shouldRecordMembershipCancelledEvent() {
        MembershipCancelled event =
                membershipCancelled();

        listener.record(
                event);

        verify(auditEntryStore)
                .recordMembershipCancelled(
                        event);
    }

    private static MembershipCancelled
    membershipCancelled() {

        return new MembershipCancelled(
                MEMBERSHIP_ID,
                "MEM-000001",
                CLIENT_ID,
                PERIOD_ID,
                LocalDate.of(
                        2026,
                        9,
                        15),
                "Client requested cancellation",
                MembershipStatus.ACTIVE,
                MembershipStatus.CANCELLED,
                false,
                ACTOR_ID,
                "coach-admin",
                NOW);
    }
}