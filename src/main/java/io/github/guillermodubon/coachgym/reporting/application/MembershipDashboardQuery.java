package io.github.guillermodubon.coachgym.reporting.application;

import io.github.guillermodubon.coachgym.reporting.MembershipDashboardDetails;

/** Read port for current membership dashboard indicators. */
public interface MembershipDashboardQuery {

    MembershipDashboardDetails summarize(
            DashboardPeriod period,
            int expirationWarningDays);
}
