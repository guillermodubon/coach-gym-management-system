package io.github.guillermodubon.coachgym.user.web;

import io.github.guillermodubon.coachgym.user.application.StaffPasswordChangeResult;
import java.util.UUID;

/** Safe password-change result; it contains no credential material. */
record StaffPasswordChangeResponse(
        UUID userId,
        long profileVersion,
        boolean reauthenticationRequired) {

    static StaffPasswordChangeResponse from(StaffPasswordChangeResult result) {
        return new StaffPasswordChangeResponse(
                result.userId(),
                result.profileVersion(),
                result.reauthenticationRequired());
    }
}
