package io.github.guillermodubon.coachgym.user.infrastructure.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.guillermodubon.coachgym.user.ActiveBranchContextUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class HttpSessionActiveBranchContextStoreTest {

    private static final UUID BRANCH_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void storesOnlyTheServerSideBranchIdentifierAndCanClearIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        HttpSessionActiveBranchContextStore store = new HttpSessionActiveBranchContextStore();

        store.select(BRANCH_ID);

        assertThat(store.selectedBranchId()).contains(BRANCH_ID);
        assertThat(request.getSession(false)
                .getAttribute(HttpSessionActiveBranchContextStore.ACTIVE_BRANCH_ID_ATTRIBUTE))
                .isEqualTo(BRANCH_ID);

        store.clear();
        assertThat(store.selectedBranchId()).isEmpty();
    }

    @Test
    void malformedLegacyValueIsDiscardedAndNeverAuthorizesAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        request.getSession(true).setAttribute(
                HttpSessionActiveBranchContextStore.ACTIVE_BRANCH_ID_ATTRIBUTE, "not-a-uuid");
        HttpSessionActiveBranchContextStore store = new HttpSessionActiveBranchContextStore();

        assertThat(store.selectedBranchId()).isEmpty();
        assertThat(request.getSession(false).getAttribute(
                HttpSessionActiveBranchContextStore.ACTIVE_BRANCH_ID_ATTRIBUTE)).isNull();
    }

    @Test
    void branchSelectionsAreIsolatedPerServerSideSession() {
        MockHttpServletRequest firstRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(firstRequest));
        HttpSessionActiveBranchContextStore store = new HttpSessionActiveBranchContextStore();
        store.select(BRANCH_ID);

        MockHttpServletRequest secondRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(secondRequest));

        assertThat(store.selectedBranchId()).isEmpty();
        assertThat(firstRequest.getSession(false).getAttribute(
                HttpSessionActiveBranchContextStore.ACTIVE_BRANCH_ID_ATTRIBUTE))
                .isEqualTo(BRANCH_ID);
    }

    @Test
    void expiredSessionCannotProvideAnActiveBranch() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        HttpSessionActiveBranchContextStore store = new HttpSessionActiveBranchContextStore();
        store.select(BRANCH_ID);

        request.getSession(false).invalidate();

        assertThat(store.selectedBranchId()).isEmpty();
    }

    @Test
    void selectionRequiresARequestBoundServerSession() {
        HttpSessionActiveBranchContextStore store = new HttpSessionActiveBranchContextStore();

        assertThatThrownBy(() -> store.select(BRANCH_ID))
                .isInstanceOf(ActiveBranchContextUnavailableException.class);
    }
}
