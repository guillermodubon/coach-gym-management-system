package io.github.guillermodubon.coachgym.reporting.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration limits owned by the reporting module. */
@Component
@ConfigurationProperties(prefix = "gym.reporting")
public class ReportingProperties {

    public static final int DEFAULT_MAXIMUM_PERIOD_DAYS = 366;

    private int maximumPeriodDays = DEFAULT_MAXIMUM_PERIOD_DAYS;

    public int getMaximumPeriodDays() {
        return maximumPeriodDays;
    }

    public void setMaximumPeriodDays(int maximumPeriodDays) {
        if (maximumPeriodDays < 1 || maximumPeriodDays > 3_660) {
            throw new ReportingValidationException(
                    "Reporting maximum period days must be between 1 and 3660.");
        }
        this.maximumPeriodDays = maximumPeriodDays;
    }
}
