import { useLayoutEffect, useRef, useState } from "react";
import {
  createBrowserRouter,
  createMemoryRouter,
  Outlet,
  RouterProvider,
  useLocation,
} from "react-router";
import type { RouteObject } from "react-router";
import { LoginPage } from "../../features/auth/LoginPage";
import { RequiredPasswordChangePage } from "../../features/auth/RequiredPasswordChangePage";
import { SessionGate } from "../../features/auth/SessionGate";
import { NotFoundPage } from "../../features/auth/session-pages";
import { VoluntaryPasswordChangePage } from "../../features/auth/VoluntaryPasswordChangePage";
import { HomePage } from "../../features/home/HomePage";
import { AccountSettingsPage } from "../../features/account/AccountSettingsPage";
import { ProfilePage } from "../../features/profile/ProfilePage";
import { AccountProfileProvider } from "../../features/profile/AccountProfileProvider";
import { ProfilePhotoProvider } from "../../features/profile/ProfilePhotoProvider";
import { PublicIdentityBoundary } from "../../features/identity/PublicIdentityBoundary";
import { PasswordRecoveryRequestPage } from "../../features/identity/PasswordRecoveryRequestPage";
import { PasswordRecoveryCompletionPage } from "../../features/identity/PasswordRecoveryCompletionPage";
import { InvitationAcceptancePage } from "../../features/identity/InvitationAcceptancePage";
import { usePublicIdentityRuntime } from "../../features/identity/PublicIdentityProvider";
import { AuthenticatedShell } from "../layouts/AuthenticatedShell";
import { RouteFocusManager } from "./RouteFocusManager";

function ApplicationRouteRoot() {
  const location = useLocation();
  const { runtime, snapshot } = usePublicIdentityRuntime();
  const previousPath = useRef(location.pathname);

  useLayoutEffect(() => {
    if (previousPath.current === location.pathname) return;
    previousPath.current = location.pathname;
    if (
      snapshot.kind !== "empty" &&
      ((snapshot.purpose === "password-recovery" &&
        location.pathname !== "/recover-password") ||
        (snapshot.purpose === "invitation" &&
          location.pathname !== "/accept-invitation"))
    ) {
      runtime.clear();
    }
  }, [location.pathname, runtime, snapshot]);

  return (
    <AccountProfileProvider>
      <ProfilePhotoProvider>
        <RouteFocusManager />
        <Outlet />
      </ProfilePhotoProvider>
    </AccountProfileProvider>
  );
}

export function createAppRouteObjects(): RouteObject[] {
  return [
    {
      element: <ApplicationRouteRoot />,
      children: [
        {
          path: "/login",
          element: (
            <SessionGate mode="public">
              <LoginPage />
            </SessionGate>
          ),
        },
        {
          path: "/solicitar-recuperacion",
          element: (
            <SessionGate mode="public-identity">
              <PublicIdentityBoundary>
                <PasswordRecoveryRequestPage />
              </PublicIdentityBoundary>
            </SessionGate>
          ),
        },
        {
          path: "/recover-password",
          element: (
            <SessionGate mode="public-identity">
              <PublicIdentityBoundary>
                <PasswordRecoveryCompletionPage />
              </PublicIdentityBoundary>
            </SessionGate>
          ),
        },
        {
          path: "/accept-invitation",
          element: (
            <SessionGate mode="public-identity">
              <PublicIdentityBoundary>
                <InvitationAcceptancePage />
              </PublicIdentityBoundary>
            </SessionGate>
          ),
        },
        {
          path: "/cambio-contrasena-obligatorio",
          element: (
            <SessionGate mode="password-required">
              <RequiredPasswordChangePage />
            </SessionGate>
          ),
        },
        {
          path: "/acceso-restringido",
          element: <SessionGate mode="restricted" />,
        },
        {
          path: "/mi-cuenta/contrasena",
          element: (
            <SessionGate mode="voluntary-password">
              <VoluntaryPasswordChangePage />
            </SessionGate>
          ),
        },
        {
          path: "/",
          element: (
            <SessionGate mode="private">
              <AuthenticatedShell />
            </SessionGate>
          ),
          children: [
            { index: true, element: <HomePage /> },
            { path: "mi-perfil", element: <ProfilePage /> },
            { path: "mi-cuenta", element: <AccountSettingsPage /> },
          ],
        },
        {
          path: "*",
          element: (
            <SessionGate mode="not-found">
              <NotFoundPage />
            </SessionGate>
          ),
        },
      ],
    },
  ];
}

export function createMemoryAppRouter(initialEntries: string[] = ["/"]) {
  return createMemoryRouter(createAppRouteObjects(), { initialEntries });
}

let browserRouter: ReturnType<typeof createBrowserRouter> | undefined;

function getBrowserRouter() {
  browserRouter ??= createBrowserRouter(createAppRouteObjects());
  return browserRouter;
}

export function AppRoutes() {
  const [router] = useState(getBrowserRouter);
  return <RouterProvider router={router} />;
}
