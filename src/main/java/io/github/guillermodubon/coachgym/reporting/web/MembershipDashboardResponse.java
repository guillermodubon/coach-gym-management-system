package io.github.guillermodubon.coachgym.reporting.web;

import io.github.guillermodubon.coachgym.reporting.MembershipDashboardDetails;

public record MembershipDashboardResponse(long active, long frozen, long expiringSoon) {
    static MembershipDashboardResponse from(MembershipDashboardDetails details) {
        return new MembershipDashboardResponse(
                details.active(), details.frozen(), details.expiringSoon());
    }
}
