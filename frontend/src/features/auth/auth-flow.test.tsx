import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { App } from "../../app/App";
import { AppProviders } from "../../app/AppProviders";
import { identityFixture } from "../../test/identity-fixtures";
import { server } from "../../test/server";

function renderApplication(entry = "/") {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <AppProviders>
        <App />
      </AppProviders>
    </MemoryRouter>,
  );
}

function anonymousResponse() {
  return HttpResponse.json(
    { status: 401, code: "AUTHENTICATION_REQUIRED" },
    { status: 401 },
  );
}

function csrfResponse() {
  return HttpResponse.json({
    token: "synthetic-csrf-token",
    headerName: "X-XSRF-TOKEN",
    parameterName: "_csrf",
  });
}

describe("B04 browser session flows with MSW", () => {
  it("clears the password before identity bootstrap and logs out explicitly", async () => {
    const user = userEvent.setup();
    let loggedIn = false;
    let passwordValueBeforeBootstrap: string | null = null;
    const loginRequests = vi.fn();
    const logoutRequests = vi.fn();

    server.use(
      http.get("/api/v1/auth/me", () => {
        if (!loggedIn) return anonymousResponse();
        passwordValueBeforeBootstrap =
          document.querySelector<HTMLInputElement>('input[type="password"]')
            ?.value ?? null;
        return HttpResponse.json(identityFixture);
      }),
      http.get("/api/v1/auth/csrf", () => csrfResponse()),
      http.post("/api/v1/auth/login", async ({ request }) => {
        loginRequests();
        expect(request.headers.get("X-XSRF-TOKEN")).toBe(
          "synthetic-csrf-token",
        );
        loggedIn = true;
        return new HttpResponse(null, { status: 204 });
      }),
      http.post("/api/v1/auth/logout", () => {
        logoutRequests();
        loggedIn = false;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderApplication();
    await screen.findByRole(
      "heading",
      { name: "Iniciar sesión" },
      { timeout: 5_000 },
    );
    await user.type(
      screen.getByRole("textbox", { name: "Usuario o correo" }),
      "staff@example.test",
    );
    await user.type(screen.getByLabelText("Contraseña"), "synthetic-password");
    await user.dblClick(screen.getByRole("button", { name: "Iniciar sesión" }));

    expect(
      await screen.findByRole("heading", { name: "Inicio" }),
    ).toBeVisible();
    expect(screen.getByText(/Has iniciado sesión en Coach Gym/)).toBeVisible();
    expect(
      screen.getByRole("group", {
        name: "Ámbito de organización · Sin sucursal activa",
      }),
    ).toBeVisible();
    expect(passwordValueBeforeBootstrap).toBe("");
    expect(loginRequests).toHaveBeenCalledTimes(1);

    await user.click(
      screen.getByRole("button", {
        name: "Menú de cuenta de Operador de prueba",
      }),
    );
    expect(
      await screen.findByText("Administrador · Organización"),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole("menuitem", { name: "Cerrar sesión" }));
    expect(
      await screen.findByRole("heading", { name: "Iniciar sesión" }),
    ).toBeVisible();
    expect(logoutRequests).toHaveBeenCalledTimes(1);
    expect(screen.getByText("Sesión cerrada.")).toBeVisible();
  }, 15_000);

  it("uses a generic credential message and clears password after a rejected login", async () => {
    const user = userEvent.setup();
    const loginRequests = vi.fn();
    server.use(
      http.get("/api/v1/auth/me", () => anonymousResponse()),
      http.get("/api/v1/auth/csrf", () => csrfResponse()),
      http.post("/api/v1/auth/login", () => {
        loginRequests();
        return HttpResponse.json(
          { status: 401, code: "INVALID_CREDENTIALS" },
          { status: 401 },
        );
      }),
    );

    renderApplication("/login");
    await screen.findByRole("heading", { name: "Iniciar sesión" });
    await user.type(
      screen.getByRole("textbox", { name: "Usuario o correo" }),
      "synthetic-user",
    );
    await user.type(screen.getByLabelText("Contraseña"), "synthetic-password");
    await user.click(screen.getByRole("button", { name: "Iniciar sesión" }));

    expect(
      await screen.findByText("Usuario o contraseña incorrectos."),
    ).toBeVisible();
    expect(screen.getByLabelText("Contraseña")).toHaveValue("");
    expect(loginRequests).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(/cuenta .*existe/i)).not.toBeInTheDocument();
  }, 15_000);

  it("limits a password-required account to the password form and logout", async () => {
    const user = userEvent.setup();
    let loggedIn = false;
    const branchContextRequests = vi.fn();
    const passwordPageRequests = vi.fn();
    server.use(
      http.get("/api/v1/auth/me", () =>
        loggedIn
          ? HttpResponse.json({
              ...identityFixture,
              passwordChangeRequired: true,
            })
          : anonymousResponse(),
      ),
      http.get("/api/v1/auth/csrf", () => csrfResponse()),
      http.get("/api/v1/me/branch-context", () => {
        branchContextRequests();
        return HttpResponse.json({});
      }),
      http.post("/api/v1/me/profile/password", () => {
        passwordPageRequests();
        return new HttpResponse(null, { status: 204 });
      }),
      http.post("/api/v1/auth/login", () => {
        loggedIn = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );

    renderApplication("/");
    await screen.findByRole("heading", { name: "Iniciar sesión" });
    await user.type(
      screen.getByRole("textbox", { name: "Usuario o correo" }),
      "synthetic-user",
    );
    await user.type(screen.getByLabelText("Contraseña"), "synthetic-password");
    await user.click(screen.getByRole("button", { name: "Iniciar sesión" }));

    expect(
      await screen.findByRole("heading", {
        name: "Actualiza tu contraseña",
      }),
    ).toBeVisible();
    expect(screen.getByLabelText("Contraseña actual")).toBeVisible();
    expect(screen.getByLabelText("Nueva contraseña")).toBeVisible();
    expect(screen.getByLabelText("Confirmar nueva contraseña")).toBeVisible();
    await waitFor(() => {
      expect(branchContextRequests).not.toHaveBeenCalled();
      expect(passwordPageRequests).not.toHaveBeenCalled();
    });
    expect(
      screen.queryByRole("heading", { name: "Inicio" }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cerrar sesión" })).toBeVisible();
  }, 15_000);

  it("changes the password once, clears it, and requires a fresh login", async () => {
    const user = userEvent.setup();
    let loggedIn = false;
    let passwordRequired = false;
    const identityRequests = vi.fn();
    const passwordChangeRequests = vi.fn();
    const loginRequests = vi.fn();
    const originalPassword = "short-current";
    const replacementPassword = "long-enough-new-password";

    server.use(
      http.get("/api/v1/auth/me", () => {
        identityRequests();
        if (!loggedIn) return anonymousResponse();
        return HttpResponse.json({
          ...identityFixture,
          passwordChangeRequired: passwordRequired,
        });
      }),
      http.get("/api/v1/auth/csrf", () => csrfResponse()),
      http.post("/api/v1/auth/login", () => {
        loginRequests();
        loggedIn = true;
        passwordRequired = true;
        return new HttpResponse(null, { status: 204 });
      }),
      http.post("/api/v1/me/profile/password", async ({ request }) => {
        passwordChangeRequests();
        expect(request.headers.get("X-XSRF-TOKEN")).toBe(
          "synthetic-csrf-token",
        );
        const body = await request.json();
        expect(Object.keys(body as object).sort()).toEqual([
          "currentPassword",
          "newPassword",
          "newPasswordConfirmation",
        ]);
        expect(body).toEqual({
          currentPassword: originalPassword,
          newPassword: replacementPassword,
          newPasswordConfirmation: replacementPassword,
        });
        passwordRequired = false;
        loggedIn = false;
        return HttpResponse.json({
          userId: identityFixture.id,
          profileVersion: 12,
          reauthenticationRequired: true,
        });
      }),
    );

    renderApplication("/");
    await screen.findByRole("heading", { name: "Iniciar sesión" });
    await user.type(
      screen.getByRole("textbox", { name: "Usuario o correo" }),
      "synthetic-user",
    );
    await user.type(screen.getByLabelText("Contraseña"), "synthetic-login");
    await user.click(screen.getByRole("button", { name: "Iniciar sesión" }));

    await screen.findByRole("heading", { name: "Actualiza tu contraseña" });
    await user.type(
      screen.getByLabelText("Contraseña actual"),
      originalPassword,
    );
    await user.type(
      screen.getByLabelText("Nueva contraseña"),
      replacementPassword,
    );
    await user.type(
      screen.getByLabelText("Confirmar nueva contraseña"),
      replacementPassword,
    );
    await user.dblClick(
      screen.getByRole("button", { name: "Actualizar contraseña" }),
    );

    expect(
      await screen.findByRole("heading", { name: "Iniciar sesión" }),
    ).toBeVisible();
    expect(
      screen.getByText("Contraseña actualizada. Inicia sesión de nuevo."),
    ).toBeVisible();
    expect(passwordChangeRequests).toHaveBeenCalledTimes(1);
    expect(loginRequests).toHaveBeenCalledTimes(1);
    expect(identityRequests).toHaveBeenCalledTimes(2);
  }, 20_000);

  it("clears all password fields after a rejected password change", async () => {
    const user = userEvent.setup();
    let loggedIn = false;
    const passwordChangeRequests = vi.fn();
    server.use(
      http.get("/api/v1/auth/me", () =>
        loggedIn
          ? HttpResponse.json({
              ...identityFixture,
              passwordChangeRequired: true,
            })
          : anonymousResponse(),
      ),
      http.get("/api/v1/auth/csrf", () => csrfResponse()),
      http.post("/api/v1/auth/login", () => {
        loggedIn = true;
        return new HttpResponse(null, { status: 204 });
      }),
      http.post("/api/v1/me/profile/password", () => {
        passwordChangeRequests();
        return HttpResponse.json(
          { status: 400, code: "CURRENT_PASSWORD_INVALID" },
          { status: 400 },
        );
      }),
    );

    renderApplication("/");
    await screen.findByRole("heading", { name: "Iniciar sesión" });
    await user.type(
      screen.getByRole("textbox", { name: "Usuario o correo" }),
      "synthetic-user",
    );
    await user.type(screen.getByLabelText("Contraseña"), "synthetic-login");
    await user.click(screen.getByRole("button", { name: "Iniciar sesión" }));
    await screen.findByRole("heading", { name: "Actualiza tu contraseña" });
    await user.type(
      screen.getByLabelText("Contraseña actual"),
      "synthetic-current",
    );
    await user.type(
      screen.getByLabelText("Nueva contraseña"),
      "synthetic-new-password",
    );
    await user.type(
      screen.getByLabelText("Confirmar nueva contraseña"),
      "synthetic-new-password",
    );
    await user.click(
      screen.getByRole("button", { name: "Actualizar contraseña" }),
    );

    expect(
      await screen.findByText(
        "No se pudo verificar la contraseña actual. Revísala e inténtalo de nuevo.",
      ),
    ).toBeVisible();
    expect(screen.getByLabelText("Contraseña actual")).toHaveValue("");
    expect(screen.getByLabelText("Nueva contraseña")).toHaveValue("");
    expect(screen.getByLabelText("Confirmar nueva contraseña")).toHaveValue("");
    expect(passwordChangeRequests).toHaveBeenCalledTimes(1);
    expect(
      screen.getByRole("heading", { name: "Actualiza tu contraseña" }),
    ).toBeVisible();
  }, 20_000);

  it("validates the new-password minimum locally while allowing a short current password", async () => {
    const user = userEvent.setup();
    const passwordChangeRequests = vi.fn();
    server.use(
      http.get("/api/v1/auth/me", () =>
        HttpResponse.json({
          ...identityFixture,
          passwordChangeRequired: true,
        }),
      ),
      http.post("/api/v1/me/profile/password", () => {
        passwordChangeRequests();
        return HttpResponse.json({
          userId: identityFixture.id,
          profileVersion: 2,
          reauthenticationRequired: true,
        });
      }),
    );

    renderApplication("/cambio-contrasena-obligatorio");
    await screen.findByRole("heading", { name: "Actualiza tu contraseña" });
    await user.type(screen.getByLabelText("Contraseña actual"), "x");
    await user.type(
      screen.getByLabelText("Nueva contraseña", { exact: true }),
      "x".repeat(11),
    );
    await user.type(
      screen.getByLabelText("Confirmar nueva contraseña", { exact: true }),
      "x".repeat(11),
    );
    await user.click(
      screen.getByRole("button", { name: "Actualizar contraseña" }),
    );

    expect(
      await screen.findByText("Usa al menos 12 caracteres."),
    ).toBeVisible();
    expect(passwordChangeRequests).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Contraseña actual")).toHaveValue("x");
  });
});
