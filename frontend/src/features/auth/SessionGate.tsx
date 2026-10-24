import type { PropsWithChildren } from "react";
import { Navigate, useLocation } from "react-router";
import { useAuth } from "./useAuth";
import { CheckingPage, SessionIssuePage } from "./session-pages";
import { safeReturnPath } from "../../app/router/return-path";

export type SessionGateMode =
  | "public"
  | "public-identity"
  | "private"
  | "password-required"
  | "voluntary-password"
  | "restricted"
  | "not-found";

export function SessionGate({
  children,
  mode,
}: PropsWithChildren<{ mode: SessionGateMode }>) {
  const auth = useAuth();
  const location = useLocation();
  const { state } = auth;

  if (
    state.kind === "password-required" &&
    mode === "password-required" &&
    auth.pendingAction === "password"
  ) {
    return children;
  }
  if (
    state.kind === "authenticated" &&
    mode === "voluntary-password" &&
    auth.pendingAction === "password"
  ) {
    return children;
  }
  if (
    state.kind === "checking" &&
    mode === "public" &&
    auth.pendingAction === "login"
  ) {
    return children;
  }
  if (state.kind === "checking" || state.kind === "signing-out") {
    return <CheckingPage />;
  }
  if (state.kind === "restricted" || state.kind === "unavailable") {
    return <SessionIssuePage />;
  }
  if (auth.privacyCovered && state.kind !== "anonymous") {
    return <CheckingPage />;
  }
  if (state.kind === "password-required") {
    if (mode === "password-required") return children;
    if (mode === "public-identity") return children;
    return <Navigate replace to="/cambio-contrasena-obligatorio" />;
  }
  if (state.kind === "authenticated") {
    if (
      mode === "private" ||
      mode === "voluntary-password" ||
      mode === "not-found"
    )
      return children;
    if (mode === "public-identity") return children;
    if (mode === "password-required" || mode === "restricted") {
      return <Navigate replace to="/" />;
    }
    const candidate =
      typeof location.state === "object" && location.state !== null
        ? location.state.returnTo
        : undefined;
    return <Navigate replace to={safeReturnPath(candidate)} />;
  }
  if (mode === "public" || mode === "public-identity") return children;
  if (mode === "not-found") return children;
  if (mode === "restricted") return <Navigate replace to="/login" />;
  return (
    <Navigate
      replace
      to="/login"
      state={{
        returnTo:
          auth.notice === "password-changed-login-again"
            ? "/"
            : safeReturnPath(location.pathname),
      }}
    />
  );
}
