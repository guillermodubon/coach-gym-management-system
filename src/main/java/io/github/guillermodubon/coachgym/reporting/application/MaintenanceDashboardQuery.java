package io.github.guillermodubon.coachgym.reporting.application;

import io.github.guillermodubon.coachgym.reporting.MaintenanceDashboardDetails;
import java.time.LocalDate;

/** Read port for current maintenance work-order indicators. */
public interface MaintenanceDashboardQuery {

    MaintenanceDashboardDetails summarize(LocalDate operationalDate);
}
