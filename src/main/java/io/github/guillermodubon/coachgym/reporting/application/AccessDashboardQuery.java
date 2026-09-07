package io.github.guillermodubon.coachgym.reporting.application;

import io.github.guillermodubon.coachgym.reporting.AccessDashboardDetails;

/** Read port for access decisions recorded during the operational day. */
public interface AccessDashboardQuery {

    AccessDashboardDetails summarizeToday(DashboardPeriod period);
}
