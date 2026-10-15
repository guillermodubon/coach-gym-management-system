package io.github.guillermodubon.coachgym.reporting.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Role-specific dashboard response; administrator and receptionist shapes never mix. */
@Schema(oneOf = {
        ReportingAdministratorDashboardResponse.class,
        ReportingReceptionistDashboardResponse.class})
public sealed interface ReportingDashboardResponse
        permits ReportingAdministratorDashboardResponse,
                ReportingReceptionistDashboardResponse {

    ReportingApiContextResponse context();
}
