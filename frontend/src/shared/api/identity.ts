import type { ApiClient } from "./client";
import type { ChangePasswordRequest, LoginRequest } from "./contracts";
import {
  parseBranchContext,
  parseCurrentUser,
  parsePasswordChange,
} from "./contracts";

export interface RequestControl {
  signal?: AbortSignal;
}

/** Direct calls intentionally keep credentials outside query keys and mutation caches. */
export function createIdentityApi(client: ApiClient) {
  return {
    currentUser: (control: RequestControl = {}) =>
      client.request({
        path: "/api/v1/auth/me",
        method: "GET",
        responseType: "json",
        expectedStatus: 200,
        parse: parseCurrentUser,
        ...control,
      }),
    branchContext: (control: RequestControl = {}) =>
      client.request({
        path: "/api/v1/me/branch-context",
        method: "GET",
        responseType: "json",
        expectedStatus: 200,
        parse: parseBranchContext,
        ...control,
      }),
    login: (credentials: LoginRequest, control: RequestControl = {}) =>
      client.request({
        path: "/api/v1/auth/login",
        method: "POST",
        expectedStatus: 204,
        responseType: "empty",
        bodyType: "json",
        body: {
          identifier: credentials.identifier,
          password: credentials.password,
        },
        parse: () => undefined,
        ...control,
      }),
    logout: (control: RequestControl = {}) =>
      client.request({
        path: "/api/v1/auth/logout",
        method: "POST",
        expectedStatus: 204,
        responseType: "empty",
        bodyType: "none",
        parse: () => undefined,
        ...control,
      }),
    changePassword: (
      credentials: ChangePasswordRequest,
      control: RequestControl = {},
    ) =>
      client.request({
        path: "/api/v1/me/profile/password",
        method: "POST",
        responseType: "json",
        bodyType: "json",
        expectedStatus: 200,
        body: {
          currentPassword: credentials.currentPassword,
          newPassword: credentials.newPassword,
          newPasswordConfirmation: credentials.newPasswordConfirmation,
        },
        parse: parsePasswordChange,
        ...control,
      }),
  };
}
