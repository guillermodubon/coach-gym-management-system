import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { RouterProvider } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { AppProviders } from "../../app/AppProviders";
import { createMemoryAppRouter } from "../../app/router/AppRoutes";
import { identityFixture } from "../../test/identity-fixtures";
import { server } from "../../test/server";
import { createPublicIdentityRuntime } from "./public-identity-runtime";

const recoveryToken = "R".repeat(43);
const genericRequestMessage =
  "Si existe una cuenta de personal activa con ese correo, recibirás instrucciones para recuperar tu contraseña.";
const profileFixture = {
  userId: identityFixture.id,
  username: identityFixture.username,
  email: "operator@example.test",
  firstName: "Operador",
  lastName: "Prueba",
  displayName: identityFixture.fullName,
  roles: identityFixture.roles,
  status: "ACTIVE",
  photoPresent: false,
  photoUrl: null,
  version: 1,
};

function csrfResponse() {
  return HttpResponse.json({
    token: "synthetic-csrf-token",
    headerName: "X-XSRF-TOKEN",
    parameterName: "_csrf",
  });
}

function anonymousResponse() {
  return HttpResponse.json(
    { status: 401, code: "AUTHENTICATION_REQUIRED" },
    { status: 401 },
  );
}

function renderApplication(
  entry: string,
  runtime = createPublicIdentityRuntime(),
) {
  const router = createMemoryAppRouter([entry]);
  const view = render(
    <AppProviders publicIdentityRuntime={runtime}>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...view, router, runtime };
}

function serveAnonymousSession() {
  server.use(
    http.get("/api/v1/auth/me", () => anonymousResponse()),
    http.get("/api/v1/auth/csrf", () => csrfResponse()),
  );
}

describe("password recovery public identity flows", () => {
  it("shows one account-independent acknowledgement for a successful request", async () => {
    const user = userEvent.setup();
    const requests = vi.fn();
    serveAnonymousSession();
    server.use(
      http.post(
        "/api/v1/auth/password-recovery-requests",
        async ({ request }) => {
          requests();
          expect(request.headers.get("X-XSRF-TOKEN")).toBe(
            "synthetic-csrf-token",
          );
          expect(await request.json()).toEqual({ email: "staff@example.test" });
          return HttpResponse.json(
            { message: "Backend-specific acknowledgement must stay hidden." },
            { status: 202 },
          );
        },
      ),
    );

    renderApplication("/solicitar-recuperacion");
    await screen.findByRole("heading", { name: "Recuperar contraseña" });
    await user.type(
      screen.getByLabelText("Correo electrónico"),
      "staff@example.test",
    );
    await user.dblClick(
      screen.getByRole("button", { name: "Enviar instrucciones" }),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      genericRequestMessage,
    );
    expect(screen.queryByText("Backend-specific acknowledgement")).toBeNull();
    expect(screen.getByLabelText("Correo electrónico")).toHaveValue("");
    expect(requests).toHaveBeenCalledTimes(1);
  }, 15_000);

  it("completes recovery with the exact password, then clears the one-time token", async () => {
    const user = userEvent.setup();
    const runtime = createPublicIdentityRuntime({
      kind: "available",
      purpose: "password-recovery",
      token: recoveryToken,
    });
    const submissions = vi.fn();
    serveAnonymousSession();
    server.use(
      http.post(
        "/api/v1/auth/password-recovery/complete",
        async ({ request }) => {
          submissions();
          expect(request.headers.get("X-XSRF-TOKEN")).toBe(
            "synthetic-csrf-token",
          );
          expect(await request.json()).toEqual({
            token: recoveryToken,
            newPassword: "  synthetic new password  ",
            passwordConfirmation: "  synthetic new password  ",
          });
          return new HttpResponse(null, { status: 204 });
        },
      ),
    );

    renderApplication("/recover-password", runtime);
    await screen.findByRole("heading", { name: "Define una contraseña nueva" });
    fireEvent.change(screen.getByLabelText("Contraseña nueva"), {
      target: { value: "  synthetic new password  " },
    });
    fireEvent.change(screen.getByLabelText("Confirma la contraseña nueva"), {
      target: { value: "  synthetic new password  " },
    });
    await user.dblClick(
      screen.getByRole("button", { name: "Actualizar contraseña" }),
    );

    expect(
      await screen.findByRole("heading", { name: "Contraseña actualizada" }),
    ).toBeVisible();
    expect(runtime.getSnapshot()).toEqual({ kind: "empty" });
    expect(submissions).toHaveBeenCalledTimes(1);
    expect(
      screen.getByRole("link", { name: "Ir a iniciar sesión" }),
    ).toBeVisible();
    expect(
      JSON.stringify({ ...localStorage, ...sessionStorage }),
    ).not.toContain(recoveryToken);
    expect(
      JSON.stringify({ ...localStorage, ...sessionStorage }),
    ).not.toContain("synthetic new password");
  }, 15_000);

  it("does not inspect or submit after a refresh without an in-memory token", async () => {
    const inspect = vi.fn();
    serveAnonymousSession();
    server.use(
      http.post("/api/v1/auth/password-recovery/complete", () => {
        inspect();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderApplication("/recover-password");
    expect(
      await screen.findByText(
        "El enlace no está disponible. Abre el enlace completo del correo o solicita uno nuevo.",
      ),
    ).toBeVisible();
    expect(screen.queryByLabelText("Contraseña nueva")).toBeNull();
    expect(inspect).not.toHaveBeenCalled();
  });

  it("uses the same generic unavailable state for recovery conflict and drops the token", async () => {
    const user = userEvent.setup();
    const runtime = createPublicIdentityRuntime({
      kind: "available",
      purpose: "password-recovery",
      token: recoveryToken,
    });
    const submissions = vi.fn();
    serveAnonymousSession();
    server.use(
      http.post("/api/v1/auth/password-recovery/complete", () => {
        submissions();
        return HttpResponse.json(
          { status: 409, code: "PASSWORD_RECOVERY_NOT_AVAILABLE" },
          { status: 409 },
        );
      }),
    );

    renderApplication("/recover-password", runtime);
    await screen.findByRole("heading", { name: "Define una contraseña nueva" });
    fireEvent.change(screen.getByLabelText("Contraseña nueva"), {
      target: { value: "synthetic new password" },
    });
    fireEvent.change(screen.getByLabelText("Confirma la contraseña nueva"), {
      target: { value: "synthetic new password" },
    });
    await user.click(
      screen.getByRole("button", { name: "Actualizar contraseña" }),
    );

    expect(
      await screen.findByText(
        "El enlace de recuperación no está disponible. Solicita uno nuevo.",
      ),
    ).toBeVisible();
    expect(runtime.getSnapshot()).toEqual({ kind: "empty" });
    expect(submissions).toHaveBeenCalledTimes(1);
    expect(screen.queryByText("PASSWORD_RECOVERY_NOT_AVAILABLE")).toBeNull();
  }, 15_000);

  it("does not replay an uncertain completion and discards its token", async () => {
    const user = userEvent.setup();
    const runtime = createPublicIdentityRuntime({
      kind: "available",
      purpose: "password-recovery",
      token: recoveryToken,
    });
    const submissions = vi.fn();
    serveAnonymousSession();
    server.use(
      http.post("/api/v1/auth/password-recovery/complete", () => {
        submissions();
        return HttpResponse.json(
          { status: 500, code: "STAFF_IDENTITY_OPERATION_FAILED" },
          { status: 500 },
        );
      }),
    );

    renderApplication("/recover-password", runtime);
    await screen.findByRole("heading", { name: "Define una contraseña nueva" });
    fireEvent.change(screen.getByLabelText("Contraseña nueva"), {
      target: { value: "synthetic new password" },
    });
    fireEvent.change(screen.getByLabelText("Confirma la contraseña nueva"), {
      target: { value: "synthetic new password" },
    });
    await user.click(
      screen.getByRole("button", { name: "Actualizar contraseña" }),
    );

    expect(
      await screen.findByText(/No pudimos confirmar el cambio/),
    ).toBeVisible();
    expect(runtime.getSnapshot()).toEqual({ kind: "empty" });
    expect(submissions).toHaveBeenCalledTimes(1);
    expect(screen.queryByLabelText("Contraseña nueva")).toBeNull();
  }, 15_000);

  it("holds a recovery link while a session is open and preserves it after explicit logout", async () => {
    const user = userEvent.setup();
    let sessionActive = true;
    const recoveryRequests = vi.fn();
    const runtime = createPublicIdentityRuntime({
      kind: "available",
      purpose: "password-recovery",
      token: recoveryToken,
    });
    server.use(
      http.get("/api/v1/auth/me", () =>
        sessionActive
          ? HttpResponse.json(identityFixture)
          : anonymousResponse(),
      ),
      http.get("/api/v1/auth/csrf", () => csrfResponse()),
      http.get("/api/v1/me/profile", () => HttpResponse.json(profileFixture)),
      http.post("/api/v1/auth/logout", () => {
        sessionActive = false;
        return new HttpResponse(null, { status: 204 });
      }),
      http.post("/api/v1/auth/password-recovery/complete", () => {
        recoveryRequests();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderApplication("/recover-password", runtime);
    expect(
      await screen.findByText("Cierra la sesión para continuar"),
    ).toBeVisible();
    expect(screen.queryByLabelText("Contraseña nueva")).toBeNull();
    expect(recoveryRequests).not.toHaveBeenCalled();

    await user.click(
      screen.getByRole("button", { name: "Cerrar sesión y continuar" }),
    );
    expect(
      await screen.findByRole("heading", {
        name: "Define una contraseña nueva",
      }),
    ).toBeVisible();
    expect(runtime.getSnapshot()).toEqual({
      kind: "available",
      purpose: "password-recovery",
      token: recoveryToken,
    });
    expect(recoveryRequests).not.toHaveBeenCalled();
  });

  it("keeps the token when logout is uncertain and exposes recovery only after confirmation", async () => {
    const user = userEvent.setup();
    let sessionActive = true;
    let logoutAttempts = 0;
    const recoveryRequests = vi.fn();
    const runtime = createPublicIdentityRuntime({
      kind: "available",
      purpose: "password-recovery",
      token: recoveryToken,
    });
    server.use(
      http.get("/api/v1/auth/me", () =>
        sessionActive
          ? HttpResponse.json(identityFixture)
          : anonymousResponse(),
      ),
      http.get("/api/v1/auth/csrf", () => csrfResponse()),
      http.get("/api/v1/me/profile", () => HttpResponse.json(profileFixture)),
      http.post("/api/v1/auth/logout", () => {
        logoutAttempts += 1;
        if (logoutAttempts === 1) {
          return HttpResponse.json(
            { status: 500, code: "STAFF_IDENTITY_OPERATION_FAILED" },
            { status: 500 },
          );
        }
        sessionActive = false;
        return new HttpResponse(null, { status: 204 });
      }),
      http.post("/api/v1/auth/password-recovery/complete", () => {
        recoveryRequests();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderApplication("/recover-password", runtime);
    await screen.findByText("Cierra la sesión para continuar");
    await user.click(
      screen.getByRole("button", { name: "Cerrar sesión y continuar" }),
    );
    expect(
      await screen.findByRole("button", {
        name: "Reintentar cierre de sesión",
      }),
    ).toBeVisible();
    expect(runtime.getSnapshot()).toMatchObject({
      kind: "available",
      purpose: "password-recovery",
      token: recoveryToken,
    });
    expect(recoveryRequests).not.toHaveBeenCalled();

    await user.click(
      screen.getByRole("button", { name: "Reintentar cierre de sesión" }),
    );
    expect(
      await screen.findByRole("heading", {
        name: "Define una contraseña nueva",
      }),
    ).toBeVisible();
    expect(runtime.getSnapshot()).toMatchObject({
      kind: "available",
      purpose: "password-recovery",
      token: recoveryToken,
    });
    expect(recoveryRequests).not.toHaveBeenCalled();
  }, 15_000);

  it("clears the captured token when the user cancels the recovery route", async () => {
    const user = userEvent.setup();
    const runtime = createPublicIdentityRuntime({
      kind: "available",
      purpose: "password-recovery",
      token: recoveryToken,
    });
    serveAnonymousSession();

    const { router } = renderApplication("/recover-password", runtime);
    await screen.findByRole("heading", { name: "Define una contraseña nueva" });
    await user.click(
      screen.getByRole("link", { name: "Cancelar y volver a iniciar sesión" }),
    );

    expect(
      await screen.findByRole("heading", { name: "Iniciar sesión" }),
    ).toBeVisible();
    expect(runtime.getSnapshot()).toEqual({ kind: "empty" });
    expect(router.state.location.pathname).toBe("/login");
  }, 15_000);

  it("keeps invitation-purpose tokens out of the recovery submission path", async () => {
    const runtime = createPublicIdentityRuntime({
      kind: "available",
      purpose: "invitation",
      token: recoveryToken,
    });
    const submissions = vi.fn();
    serveAnonymousSession();
    server.use(
      http.post("/api/v1/auth/password-recovery/complete", () => {
        submissions();
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderApplication("/recover-password", runtime);
    expect(
      await screen.findByText(
        "El enlace no está disponible. Abre el enlace completo del correo o solicita uno nuevo.",
      ),
    ).toBeVisible();
    expect(submissions).not.toHaveBeenCalled();
  });
});
