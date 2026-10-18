import { Route, Routes } from "react-router";
import { LoginPage } from "../../features/auth/LoginPage";
import { RequiredPasswordChangePage } from "../../features/auth/RequiredPasswordChangePage";
import { SessionGate } from "../../features/auth/SessionGate";
import { NotFoundPage } from "../../features/auth/session-pages";
import { HomePage } from "../../features/home/HomePage";
import { RouteFocusManager } from "./RouteFocusManager";

export function AppRoutes() {
  return (
    <>
      <RouteFocusManager />
      <Routes>
        <Route
          element={
            <SessionGate mode="public">
              <LoginPage />
            </SessionGate>
          }
          path="/login"
        />
        <Route
          element={
            <SessionGate mode="private">
              <HomePage />
            </SessionGate>
          }
          path="/"
        />
        <Route
          element={
            <SessionGate mode="password-required">
              <RequiredPasswordChangePage />
            </SessionGate>
          }
          path="/cambio-contrasena-obligatorio"
        />
        <Route
          element={<SessionGate mode="restricted" />}
          path="/acceso-restringido"
        />
        <Route
          element={
            <SessionGate mode="not-found">
              <NotFoundPage />
            </SessionGate>
          }
          path="*"
        />
      </Routes>
    </>
  );
}
