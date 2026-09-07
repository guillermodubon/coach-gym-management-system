package io.github.guillermodubon.coachgym.reporting.application;

/** Read port for the business settings used by dashboard calculations. */
public interface DashboardSettingsQuery {

    DashboardSettings load();
}
