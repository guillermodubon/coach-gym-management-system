package io.github.guillermodubon.coachgym.user.infrastructure.session;

import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import io.github.guillermodubon.coachgym.user.application.ActiveBranchSessionStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Stores only the selected branch id in the authenticated server-side session. */
@Component
class HttpSessionActiveBranchContextStore implements ActiveBranchSessionStore {

    static final String ACTIVE_BRANCH_ID_ATTRIBUTE =
            HttpSessionActiveBranchContextStore.class.getName() + ".activeBranchId";

    @Override
    public Optional<UUID> selectedBranchId() {
        HttpSession session = currentSession(false);
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.getAttribute(ACTIVE_BRANCH_ID_ATTRIBUTE);
        if (value instanceof UUID branchId) {
            return Optional.of(branchId);
        }
        if (value instanceof String serialized) {
            try {
                UUID branchId = UUID.fromString(serialized);
                session.setAttribute(ACTIVE_BRANCH_ID_ATTRIBUTE, branchId);
                return Optional.of(branchId);
            } catch (IllegalArgumentException ignored) {
                // A malformed legacy value is discarded below and never authorizes access.
            }
        }
        if (value != null) {
            session.removeAttribute(ACTIVE_BRANCH_ID_ATTRIBUTE);
        }
        return Optional.empty();
    }

    @Override
    public void select(UUID branchId) {
        if (branchId == null) {
            throw new IllegalArgumentException("branchId is required");
        }
        HttpServletRequest request = currentRequest();
        request.getSession(true).setAttribute(ACTIVE_BRANCH_ID_ATTRIBUTE, branchId);
    }

    @Override
    public void clear() {
        HttpSession session = currentSession(false);
        if (session != null) {
            session.removeAttribute(ACTIVE_BRANCH_ID_ATTRIBUTE);
        }
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        throw new ActiveBranchContextUnavailableException(
                "An HTTP session is required to select an active branch.");
    }

    private static HttpSession currentSession(boolean create) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getSession(create);
        }
        return null;
    }
}
