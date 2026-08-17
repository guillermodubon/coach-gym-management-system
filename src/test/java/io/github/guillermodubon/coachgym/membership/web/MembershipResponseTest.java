package io.github.guillermodubon.coachgym.membership.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.guillermodubon.coachgym.membership.MembershipDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodDetails;
import io.github.guillermodubon.coachgym.membership.MembershipPeriodSource;
import io.github.guillermodubon.coachgym.membership.MembershipStatus;
import io.github.guillermodubon.coachgym.membership.domain.MembershipPricingSnapshot;
import io.github.guillermodubon.coachgym.plan.DurationUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipResponseTest {

    private static final UUID MEMBERSHIP_ID =
            UUID.fromString(
                    "93278d9e-66ed-48ba-8fd3-9bda275ca848");

    private static final UUID CLIENT_ID =
            UUID.fromString(
                    "e644eddf-f449-4c68-a129-b6b504488669");

    private static final UUID PERIOD_ID =
            UUID.fromString(
                    "41c703cc-deeb-424f-a881-0088fd586470");

    private static final UUID PLAN_ID =
            UUID.fromString(
                    "2cdd6a51-6740-45d2-b638-afcef26cce54");

    private static final Instant NOW =
            Instant.parse("2026-08-16T20:00:00Z");

    @Test
    void mapsMembershipWithoutPromotion() {
        MembershipPricingSnapshot pricing =
                MembershipPricingSnapshot.withoutPromotion(
                        PLAN_ID,
                        "PLAN-000001",
                        "Monthly Access",
                        1,
                        DurationUnit.MONTH,
                        new BigDecimal("25.00"),
                        "USD");

        MembershipPeriodDetails period =
                new MembershipPeriodDetails(
                        PERIOD_ID,
                        (short) 1,
                        MembershipPeriodSource.INITIAL,
                        pricing,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 1),
                        NOW,
                        0);

        MembershipDetails membership =
                new MembershipDetails(
                        MEMBERSHIP_ID,
                        "MEM-000001",
                        CLIENT_ID,
                        MembershipStatus.ACTIVE,
                        period,
                        NOW,
                        NOW,
                        0);

        MembershipResponse response =
                MembershipResponse.from(
                        membership);

        assertThat(response.id())
                .isEqualTo(MEMBERSHIP_ID);

        assertThat(response.membershipCode())
                .isEqualTo("MEM-000001");

        assertThat(response.clientId())
                .isEqualTo(CLIENT_ID);

        assertThat(response.status())
                .isEqualTo(MembershipStatus.ACTIVE);

        assertThat(response.currentPeriod().periodNumber())
                .isEqualTo((short) 1);

        assertThat(
                response.currentPeriod()
                        .pricing()
                        .planCode())
                .isEqualTo("PLAN-000001");

        assertThat(
                response.currentPeriod()
                        .pricing()
                        .promotion())
                .isNull();

        assertThat(
                response.currentPeriod()
                        .pricing()
                        .discountAmount())
                .isEqualByComparingTo("0.00");

        assertThat(
                response.currentPeriod()
                        .pricing()
                        .finalPrice())
                .isEqualByComparingTo("25.00");
    }
}
