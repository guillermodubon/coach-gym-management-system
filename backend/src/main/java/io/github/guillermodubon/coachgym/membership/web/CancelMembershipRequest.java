package io.github.guillermodubon.coachgym.membership.web;

import io.github.guillermodubon.coachgym.membership.application.CancelMembershipCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(
        name = "CancelMembershipRequest",
        description = """
                Request used by an administrator to permanently cancel
                an active or frozen membership.
                """)
public record CancelMembershipRequest(

        @NotNull
        @Schema(
                description = """
                        Effective cancellation date. It must belong to
                        the current membership period and must not be
                        after the current operational date.
                        """,
                example = "2026-08-24",
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        LocalDate cancelledOn,

        @NotBlank
        @Size(max = 2_000)
        @Schema(
                description =
                        "Administrative reason for the cancellation.",
                example =
                        "Client requested cancellation",
                maxLength = 2_000,
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        String reason,

        @NotNull
        @PositiveOrZero
        @Schema(
                description = """
                        Current optimistic-lock version of the
                        membership.
                        """,
                example = "0",
                minimum = "0",
                requiredMode =
                        Schema.RequiredMode.REQUIRED)
        Long version) {

    CancelMembershipCommand toCommand() {

        return new CancelMembershipCommand(
                cancelledOn,
                reason,
                version);
    }
}