import { createContext, useEffect, useMemo, useState } from "react";
import { useSyncExternalStore } from "react";
import type { PropsWithChildren } from "react";
import type { QueryClient } from "@tanstack/react-query";
import { useApiServices } from "../../shared/api/ApiServicesProvider";
import type {
  ChangePasswordRequest,
  LoginRequest,
  PasswordChangeResult,
} from "../../shared/api/contracts";
import type { ApiError } from "../../shared/api/errors";
import {
  createBrowserSessionEvents,
  createBrowserVisibilityEvents,
} from "./session-events";
import { createAuthController } from "./auth-controller";
import type { AuthSnapshot, MutationTicket } from "./auth-model";

export interface AuthContextValue extends AuthSnapshot {
  beginLogin(): Promise<MutationTicket | null>;
  sendLogin(ticket: MutationTicket, credentials: LoginRequest): Promise<void>;
  completeLogin(ticket: MutationTicket): Promise<void>;
  failLogin(ticket: MutationTicket, error: ApiError): Promise<void>;
  beginPasswordChange(): Promise<MutationTicket | null>;
  beginPublicIdentityAction(
    kind:
      | "recovery-request"
      | "recovery-complete"
      | "invitation-inspect"
      | "invitation-accept",
  ): Promise<MutationTicket | null>;
  finishPublicIdentityAction(ticket: MutationTicket): void;
  sendPasswordChange(
    ticket: MutationTicket,
    credentials: ChangePasswordRequest,
  ): Promise<PasswordChangeResult | null>;
  completePasswordChange(
    ticket: MutationTicket,
    result: PasswordChangeResult,
  ): Promise<void>;
  failPasswordChange(ticket: MutationTicket, error: ApiError): Promise<void>;
  logout(): Promise<void>;
  revalidate(): Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({
  children,
  queryClient,
}: PropsWithChildren<{ queryClient: QueryClient }>) {
  const { apiClient, identityApi } = useApiServices();
  const [controller] = useState(() => {
    const controller = createAuthController({
      apiClient,
      identityApi,
      queryClient,
      now: () => Date.now(),
      visibilityEvents: createBrowserVisibilityEvents(),
      sessionEvents: createBrowserSessionEvents(),
    });
    return controller;
  });
  const snapshot = useSyncExternalStore(
    controller.subscribe,
    controller.getSnapshot,
    controller.getSnapshot,
  );

  useEffect(() => controller.start(), [controller]);

  const value = useMemo<AuthContextValue>(
    () => ({
      ...snapshot,
      beginLogin: () => controller.beginCredentialAction("login"),
      sendLogin: (ticket, credentials) =>
        controller.sendLogin(ticket, credentials),
      completeLogin: (ticket) => controller.completeLogin(ticket),
      failLogin: (ticket, error) => controller.failLogin(ticket, error),
      beginPasswordChange: () => controller.beginCredentialAction("password"),
      beginPublicIdentityAction: (kind) =>
        controller.beginPublicIdentityAction(kind),
      finishPublicIdentityAction: (ticket) =>
        controller.finishPublicIdentityAction(ticket),
      sendPasswordChange: (ticket, credentials) => {
        if (ticket.kind !== "password" || !controller.isTicketCurrent(ticket)) {
          return Promise.resolve(null);
        }
        return identityApi.changePassword(credentials, {
          signal: ticket.signal,
        });
      },
      completePasswordChange: (ticket, result) =>
        controller.completePasswordChange(ticket, result),
      failPasswordChange: (ticket, error) =>
        controller.failPasswordChange(ticket, error),
      logout: () => controller.logout(),
      revalidate: () => controller.revalidate("manual"),
    }),
    [controller, snapshot],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
