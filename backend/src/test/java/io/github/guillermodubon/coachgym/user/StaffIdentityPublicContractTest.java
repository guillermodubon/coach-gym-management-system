package io.github.guillermodubon.coachgym.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffIdentityPublicContractTest {

    private static final UUID USER_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_ID = UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-09-24T12:00:00Z");

    @Test
    void commandsNormalizeValuesAndRejectInvalidInputs() {
        CreateStaffInvitationCommand invitation = new CreateStaffInvitationCommand(
                "  Staff.User@Example.Test ",
                RoleCode.RECEPTIONIST,
                StaffScopeType.BRANCH,
                List.of(BRANCH_ID));
        RequestPasswordRecoveryCommand recovery =
                new RequestPasswordRecoveryCommand("  Staff.User@Example.Test ");

        assertThat(invitation.email()).isEqualTo("staff.user@example.test");
        assertThat(recovery.email()).isEqualTo(invitation.email());
        assertThat(invitation.proposedBranchIds()).containsExactly(BRANCH_ID);

        AcceptStaffInvitationCommand acceptance = new AcceptStaffInvitationCommand(
                generatedToken(), testCredential(), testCredential(), "  Morgan   Lee  ", "  Coach ");
        assertThat(acceptance.firstName()).isEqualTo("Morgan Lee");
        assertThat(acceptance.lastName()).isEqualTo("Coach");

        assertThatThrownBy(() -> new RequestPasswordRecoveryCommand("not-an-email"))
                .isInstanceOf(StaffIdentityValidationException.class);
        assertThatThrownBy(() -> new CreateStaffInvitationCommand(
                "user@example.test", RoleCode.RECEPTIONIST, StaffScopeType.ORGANIZATION, Set.of()))
                .isInstanceOf(StaffScopeValidationException.class);
        assertThatThrownBy(() -> new ChangeStaffRoleScopeCommand(
                USER_ID,
                Set.of(RoleCode.RECEPTIONIST),
                StaffScopeType.ORGANIZATION,
                "role adjustment",
                0))
                .isInstanceOf(StaffScopeValidationException.class);
        assertThatThrownBy(() -> new AcceptStaffInvitationCommand(
                generatedToken(), "short", "short", "Morgan", "Lee"))
                .isInstanceOf(StaffIdentityValidationException.class);
        assertThatThrownBy(() -> new AcceptStaffInvitationCommand(
                generatedToken(), testCredential(), testCredential() + "different", "Morgan", "Lee"))
                .isInstanceOf(StaffIdentityValidationException.class);
        assertThatThrownBy(() -> new CompletePasswordRecoveryCommand(
                generatedToken(), testCredential(), testCredential() + "different"))
                .isInstanceOf(StaffIdentityValidationException.class);
    }

    @Test
    void acceptanceContractAllowsOnlyTokenPasswordAndApprovedProfileFields() {
        Set<String> fields = recordComponentNames(AcceptStaffInvitationCommand.class);
        assertThat(fields).containsExactlyInAnyOrder(
                "token", "password", "passwordConfirmation", "firstName", "lastName");
        assertThat(fields).doesNotContain(
                "email", "role", "roles", "scope", "assignments", "branchAssignments",
                "status", "permissions", "actor", "actorUserId", "userId");

        assertThat(recordComponentNames(CompletePasswordRecoveryCommand.class))
                .containsExactlyInAnyOrder("token", "newPassword", "passwordConfirmation");
        assertThat(recordComponentNames(CreateStaffInvitationCommand.class))
                .containsExactlyInAnyOrder("email", "proposedRole", "proposedScope", "proposedBranchIds");
        assertThat(recordComponentNames(ChangeStaffIdentityStatusCommand.class))
                .containsExactlyInAnyOrder(
                        "targetUserId", "requestedStatus", "reason", "expectedVersion");
        assertThat(recordComponentNames(ChangeStaffRoleScopeCommand.class))
                .containsExactlyInAnyOrder(
                        "targetUserId", "requestedRoles", "requestedScope", "reason", "expectedVersion");
    }

    @Test
    void tokensPasswordsAndEmailAreRedactedFromDiagnosticStringsAndSafeDetails() {
        String token = generatedToken();
        String password = testCredential();
        AcceptStaffInvitationCommand acceptance = new AcceptStaffInvitationCommand(
                token, password, password, "Morgan", "Lee");
        CompletePasswordRecoveryCommand completion =
                new CompletePasswordRecoveryCommand(token, password, password);
        RequestPasswordRecoveryCommand recovery =
                new RequestPasswordRecoveryCommand("private.person@example.test");
        CreateStaffInvitationCommand invitation = new CreateStaffInvitationCommand(
                "private.person@example.test", RoleCode.ADMIN, StaffScopeType.ORGANIZATION, Set.of());

        assertThat(acceptance.toString()).doesNotContain(token, password);
        assertThat(completion.toString()).doesNotContain(token, password);
        assertThat(recovery.toString()).doesNotContain("private.person@example.test");
        assertThat(invitation.toString()).doesNotContain("private.person@example.test");

        StaffInvitationDetails details = new StaffInvitationDetails(
                UUID.randomUUID(),
                "private.person@example.test",
                RoleCode.ADMIN,
                StaffScopeType.ORGANIZATION,
                Set.of(),
                StaffInvitationStatus.PENDING,
                CREATED_AT,
                CREATED_AT,
                CREATED_AT.plus(StaffInvitationPolicy.ADMIN_INVITATION_LIFETIME),
                0);
        assertThat(details.toString()).doesNotContain("private.person@example.test", token, password);
        assertThat(recordComponentNames(StaffInvitationDetails.class))
                .doesNotContain("token", "tokenHash", "tokenFingerprint", "password", "sessionId", "actorUserId");
    }

    @Test
    void tokenPolicyBoundsInputToFortyThreeUrlSafeUnpaddedCharacters() {
        String token = generatedToken();
        assertThat(token).hasSize(StaffTokenPolicy.TOKEN_LENGTH);
        assertThat(StaffTokenPolicy.requirePresentedToken(token)).isEqualTo(token);
        assertThatThrownBy(() -> StaffTokenPolicy.requirePresentedToken(token.substring(1)))
                .isInstanceOf(StaffIdentityValidationException.class)
                .hasMessageNotContaining(token);
        assertThatThrownBy(() -> StaffTokenPolicy.requirePresentedToken(token + "="))
                .isInstanceOf(StaffIdentityValidationException.class);
    }

    @Test
    void publicContractsAreTechnologyNeutralAndOwnedByTheUserModule() {
        List<Class<?>> types = List.of(
                StaffInvitationStatus.class,
                StaffIdentityStatus.class,
                PasswordRecoveryStatus.class,
                StaffInvitationDetails.class,
                CreateStaffInvitationCommand.class,
                AcceptStaffInvitationCommand.class,
                ChangeStaffIdentityStatusCommand.class,
                ChangeStaffRoleScopeCommand.class,
                RequestPasswordRecoveryCommand.class,
                CompletePasswordRecoveryCommand.class,
                StaffInvitationLifecyclePolicy.class,
                StaffIdentityLifecyclePolicy.class,
                PasswordRecoveryLifecyclePolicy.class,
                StaffInvitationPolicy.class,
                PasswordRecoveryPolicy.class,
                StaffTokenPolicy.class,
                StaffCredentialPolicy.class,
                LastOrganizationAdministratorPolicy.class);

        for (Class<?> type : types) {
            assertThat(type.getPackageName()).isEqualTo("io.github.guillermodubon.coachgym.user");
            Arrays.stream(type.getDeclaredFields())
                    .filter(field -> !field.isSynthetic())
                    .forEach(field -> assertThat(field.getType().getName())
                            .doesNotContain(
                                    "org.springframework",
                                    "jakarta.",
                                    "javax.",
                                    ".infrastructure",
                                    "servlet",
                                    "smtp",
                                    "resend",
                                    "gmail",
                                    "provider"));
        }
    }

    private static Set<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String generatedToken() {
        byte[] entropy = new byte[32];
        new SecureRandom().nextBytes(entropy);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    }

    private static String testCredential() {
        return "x".repeat(StaffCredentialPolicy.MIN_PASSWORD_LENGTH + 4);
    }
}
