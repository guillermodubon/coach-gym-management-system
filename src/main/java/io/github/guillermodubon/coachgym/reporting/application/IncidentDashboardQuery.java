package io.github.guillermodubon.coachgym.reporting.application;

import io.github.guillermodubon.coachgym.reporting.IncidentDashboardDetails;

/** Read port for the current unresolved incident snapshot. */
public interface IncidentDashboardQuery {

    IncidentDashboardDetails summarize();
}
