package io.github.guillermodubon.coachgym.reporting.application;

import io.github.guillermodubon.coachgym.reporting.EquipmentDashboardDetails;

/** Read port for the current non-retired equipment snapshot. */
public interface EquipmentDashboardQuery {

    EquipmentDashboardDetails summarize();
}
