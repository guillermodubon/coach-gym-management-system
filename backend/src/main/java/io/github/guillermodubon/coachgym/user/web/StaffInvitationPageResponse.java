package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.StaffInvitationPageDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
record StaffInvitationPageResponse(
        List<StaffInvitationResponse> items,
        int page,
        int size,
        boolean hasNext) {

    static StaffInvitationPageResponse from(
            StaffInvitationPageDetails page,
            int pageNumber,
            int size) {
        return new StaffInvitationPageResponse(
                page.invitations().stream().map(StaffInvitationResponse::from).toList(),
                pageNumber,
                size,
                page.hasNext());
    }
}
