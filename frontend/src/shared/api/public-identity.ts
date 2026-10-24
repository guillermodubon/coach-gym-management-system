import type { ApiClient } from "./client";
import type { RequestControl } from "./identity";
import type {
  PasswordRecoveryCompletion,
  PasswordRecoveryRequest,
  StaffInvitationAcceptanceRequest,
  StaffInvitationPreview,
  StaffInvitationAcceptance,
  PasswordRecoveryAcknowledgement,
} from "./public-identity-contracts";
import {
  parsePasswordRecoveryAcknowledgement,
  parseStaffInvitationAcceptance,
  parseStaffInvitationPreview,
} from "./public-identity-contracts";

/** Secret-bearing public identity calls remain direct and uncached. */
export function createPublicIdentityApi(client: ApiClient) {
  return {
    requestPasswordRecovery: (
      request: PasswordRecoveryRequest,
      control: RequestControl = {},
    ): Promise<PasswordRecoveryAcknowledgement> =>
      client.request({
        path: "/api/v1/auth/password-recovery-requests",
        method: "POST",
        bodyType: "json",
        body: { email: request.email },
        responseType: "json",
        expectedStatus: 202,
        parse: parsePasswordRecoveryAcknowledgement,
        ...control,
      }),
    completePasswordRecovery: (
      request: PasswordRecoveryCompletion,
      control: RequestControl = {},
    ): Promise<void> =>
      client.request({
        path: "/api/v1/auth/password-recovery/complete",
        method: "POST",
        bodyType: "json",
        body: {
          token: request.token,
          newPassword: request.newPassword,
          passwordConfirmation: request.passwordConfirmation,
        },
        responseType: "empty",
        expectedStatus: 204,
        parse: () => undefined,
        ...control,
      }),
    inspectStaffInvitation: (
      token: string,
      control: RequestControl = {},
    ): Promise<StaffInvitationPreview> =>
      client.request({
        path: "/api/v1/staff-invitations/inspect",
        method: "POST",
        bodyType: "json",
        body: { token },
        responseType: "json",
        expectedStatus: 200,
        parse: parseStaffInvitationPreview,
        ...control,
      }),
    acceptStaffInvitation: (
      request: StaffInvitationAcceptanceRequest,
      control: RequestControl = {},
    ): Promise<StaffInvitationAcceptance> =>
      client.request({
        path: "/api/v1/staff-invitations/accept",
        method: "POST",
        bodyType: "json",
        body: {
          token: request.token,
          password: request.password,
          passwordConfirmation: request.passwordConfirmation,
          firstName: request.firstName,
          lastName: request.lastName,
        },
        responseType: "json",
        expectedStatus: 201,
        parse: parseStaffInvitationAcceptance,
        ...control,
      }),
  };
}
