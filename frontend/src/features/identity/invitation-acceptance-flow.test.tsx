import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { RouterProvider } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { AppProviders } from "../../app/AppProviders";
import { createMemoryAppRouter } from "../../app/router/AppRoutes";
import { identityFixture } from "../../test/identity-fixtures";
import { server } from "../../test/server";
import { createPublicIdentityRuntime } from "./public-identity-runtime";

const invitationToken = "I".repeat(43);
const invitationId = "11111111-1111-4111-8111-111111111111";
const branchId = "22222222-2222-4222-8222-222222222222";
const branch = {
  branchId,
  code: "CENTRAL",
  name: "Sucursal central",
};

function preview(
  proposedRole: "ADMIN" | "RECEPTIONIST",
  proposedScope: "ORGANIZATION" | "BRANCH",
  proposedBranches: (typeof branch)[] = [],
) {
  return {
    invitationId,
    maskedEmail: "g***@example.test",
    proposedRole,
    proposedScope,
    proposedBranches,
    expiresAt: "2030-02-03T12:30:00Z",
  };
}

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

describe("staff invitation acceptance flow", () => {
  it.each([
    {
      name: "organization administrator proposal",
      role: "ADMIN" as const,
      scope: "ORGANIZATION" as const,
      branches: [] as (typeof branch)[],
      expectedRole: "Administrador",
      expectedScope: "Toda la organización",
    },
    {
      name: "branch administrator proposal",
      role: "ADMIN" as const,
      scope: "BRANCH" as const,
      branches: [branch],
      expectedRole: "Administrador",
      expectedScope: "Sucursales específicas",
    },
    {
      name: "receptionist proposal",
      role: "RECEPTIONIST" as const,
      scope: "BRANCH" as const,
      branches: [branch],
      expectedRole: "Recepcionista",
      expectedScope: "Sucursales específicas",
    },
  ])(
    "inspects and displays the immutable $name only after review",
    async (scenario) => {
      const user = userEvent.setup();
      const inspect = vi.fn();
      const accept = vi.fn();
      serveAnonymousSession();
      server.use(
        http.post("/api/v1/staff-invitations/inspect", async ({ request }) => {
          inspect();
          expect(request.headers.get("X-XSRF-TOKEN")).toBe(
            "synthetic-csrf-token",
          );
          expect(await request.json()).toEqual({ token: invitationToken });
          return HttpResponse.json(
            preview(scenario.role, scenario.scope, scenario.branches),
          );
        }),
        http.post("/api/v1/staff-invitations/accept", () => {
          accept();
          return HttpResponse.json({}, { status: 201 });
        }),
      );
      const runtime = createPublicIdentityRuntime({
        kind: "available",
        purpose: "invitation",
        token: invitationToken,
      });

      const { container } = renderApplication("/accept-invitation", runtime);
      await screen.findByRole("heading", { name: "Aceptar invitación" });

      expect(inspect).not.toHaveBeenCalled();
      expect(accept).not.toHaveBeenCalled();
      expect(container).not.toHaveTextContent(invitationToken);

      await user.click(
        screen.getByRole("button", { name: "Revisar invitación" }),
      );

      expect(await screen.findByText("g***@example.test")).toBeVisible();
      expect(screen.getByText(scenario.expectedRole)).toBeVisible();
      expect(screen.getByText(scenario.expectedScope)).toBeVisible();
      expect(container).not.toHaveTextContent(invitationId);
      expect(container).not.toHaveTextContent("guillermo@example.test");
      expect(inspect).toHaveBeenCalledTimes(1);
      expect(accept).not.toHaveBeenCalled();
      if (scenario.branches.length > 0) {
        expect(screen.getByText("Sucursal central")).toBeVisible();
        expect(screen.getByText("CENTRAL")).toBeVisible();
        expect(container).not.toHaveTextContent(branchId);
      } else {
        expect(
          screen.getByText(
            "El acceso propuesto corresponde a toda la organización.",
          ),
        ).toBeVisible();
      }
    },
  );

  it("sends the exact allowlist once and ends with manual login", async () => {
    const user = userEvent.setup();
    const inspect = vi.fn();
    const submissions = vi.fn();
    const currentUserRequests = vi.fn();
    serveAnonymousSession();
    server.use(
      http.get("/api/v1/auth/me", () => {
        currentUserRequests();
        return anonymousResponse();
      }),
      http.post("/api/v1/staff-invitations/inspect", () => {
        inspect();
        return HttpResponse.json(preview("RECEPTIONIST", "BRANCH", [branch]));
      }),
      http.post("/api/v1/staff-invitations/accept", async ({ request }) => {
        submissions();
        expect(request.headers.get("X-XSRF-TOKEN")).toBe(
          "synthetic-csrf-token",
        );
        expect(await request.json()).toEqual({
          token: invitationToken,
          firstName: "Ana María",
          lastName: "Pérez",
          password: "  synthetic invitation password  ",
          passwordConfirmation: "  synthetic invitation password  ",
        });
        return HttpResponse.json(
          {
            userId: "33333333-3333-4333-8333-333333333333",
            role: "RECEPTIONIST",
            scope: "BRANCH",
            branchIds: [branchId],
            acceptedAt: "2030-02-03T12:30:00Z",
          },
          { status: 201 },
        );
      }),
    );
    const runtime = createPublicIdentityRuntime({
      kind: "available",
      purpose: "invitation",
      token: invitationToken,
    });

    const { container } = renderApplication("/accept-invitation", runtime);
    await screen.findByRole("heading", { name: "Aceptar invitación" });
    expect(inspect).not.toHaveBeenCalled();
    expect(submissions).not.toHaveBeenCalled();

    await user.click(
      screen.getByRole("button", { name: "Revisar invitación" }),
    );
    await screen.findByText("g***@example.test");
    await user.type(screen.getByLabelText("Nombre"), "  Ana   María ");
    await user.type(screen.getByLabelText("Apellido"), " Pérez ");
    await user.type(
      screen.getByLabelText("Contraseña"),
      "  synthetic invitation password  ",
    );
    await user.type(
      screen.getByLabelText("Confirma la contraseña"),
      "  synthetic invitation password  ",
    );
    await user.dblClick(
      screen.getByRole("button", { name: "Aceptar invitación" }),
    );

    expect(
      await screen.findByRole("heading", { name: "Invitación aceptada" }),
    ).toBeVisible();
    expect(
      screen.getByRole("link", { name: "Ir a iniciar sesión" }),
    ).toHaveAttribute("href", "/login");
    expect(runtime.getSnapshot()).toEqual({ kind: "empty" });
    expect(inspect).toHaveBeenCalledTimes(1);
    expect(submissions).toHaveBeenCalledTimes(1);
    expect(currentUserRequests).toHaveBeenCalledTimes(1);
    expect(container).not.toHaveTextContent(invitationToken);
    expect(container).not.toHaveTextContent(invitationId);
    expect(container).not.toHaveTextContent(
      "33333333-3333-4333-8333-333333333333",
    );
    expect(container).not.toHaveTextContent("synthetic invitation password");
    expect(
      JSON.stringify({ ...localStorage, ...sessionStorage }),
    ).not.toContain(invitationToken);
    expect(
      JSON.stringify({ ...localStorage, ...sessionStorage }),
    ).not.toContain("synthetic invitation password");
    await waitFor(() => expect(submissions).toHaveBeenCalledTimes(1));
  }, 15_000);

  it.each(["inválida", "vencida", "revocada", "usada"])(
    "shows the same unavailable message for an invitation %s",
    async () => {
      const user = userEvent.setup();
      const inspect = vi.fn();
      const accept = vi.fn();
      serveAnonymousSession();
      server.use(
        http.post("/api/v1/staff-invitations/inspect", () => {
          inspect();
          return HttpResponse.json(
            { status: 409, code: "INVITATION_NOT_AVAILABLE" },
            { status: 409 },
          );
        }),
        http.post("/api/v1/staff-invitations/accept", () => {
          accept();
          return HttpResponse.json({}, { status: 201 });
        }),
      );
      const runtime = createPublicIdentityRuntime({
        kind: "available",
        purpose: "invitation",
        token: invitationToken,
      });

      renderApplication("/accept-invitation", runtime);
      await screen.findByRole("heading", { name: "Aceptar invitación" });
      await user.click(
        screen.getByRole("button", { name: "Revisar invitación" }),
      );

      expect(
        await screen.findByText(
          "El enlace de invitación no está disponible. Solicita a administración que envíe uno nuevo.",
        ),
      ).toBeVisible();
      expect(runtime.getSnapshot()).toEqual({ kind: "empty" });
      expect(inspect).toHaveBeenCalledTimes(1);
      expect(accept).not.toHaveBeenCalled();
    },
  );

  it.each([
    [
      "validación HTTP",
      HttpResponse.json(
        { status: 400, code: "VALIDATION_FAILED" },
        { status: 400 },
      ),
    ],
    [
      "respuesta incoherente",
      HttpResponse.json({ maskedEmail: "full@example.test" }),
    ],
  ])("fails closed on %s from inspection", async (_name, response) => {
    const user = userEvent.setup();
    serveAnonymousSession();
    server.use(http.post("/api/v1/staff-invitations/inspect", () => response));
    const runtime = createPublicIdentityRuntime({
      kind: "available",
      purpose: "invitation",
      token: invitationToken,
    });

    const { container } = renderApplication("/accept-invitation", runtime);
    await screen.findByRole("heading", { name: "Aceptar invitación" });
    await user.click(
      screen.getByRole("button", { name: "Revisar invitación" }),
    );

    expect(
      await screen.findByText(
        "El enlace de invitación no está disponible. Solicita a administración que envíe uno nuevo.",
      ),
    ).toBeVisible();
    expect(runtime.getSnapshot()).toEqual({ kind: "empty" });
    expect(container).not.toHaveTextContent("full@example.test");
    expect(screen.queryByLabelText("Nombre")).toBeNull();
  });

  it("clears the invite when it becomes unavailable between review and acceptance", async () => {
    const user = userEvent.setup();
    const submissions = vi.fn();
    serveAnonymousSession();
    server.use(
      http.post("/api/v1/staff-invitations/inspect", () =>
        HttpResponse.json(preview("ADMIN", "ORGANIZATION")),
      ),
      http.post("/api/v1/staff-invitations/accept", () => {
        submissions();
        return HttpResponse.json(
          { status: 409, code: "INVITATION_NOT_AVAILABLE" },
          { status: 409 },
        );
      }),
    );
    const runtime = createPublicIdentityRuntime({
      kind: "available",
      purpose: "invitation",
      token: invitationToken,
    });

    renderApplication("/accept-invitation", runtime);
    await screen.findByRole("heading", { name: "Aceptar invitación" });
    await user.click(
      screen.getByRole("button", { name: "Revisar invitación" }),
    );
    await screen.findByText("g***@example.test");
    await user.type(screen.getByLabelText("Nombre"), "Ana");
    await user.type(screen.getByLabelText("Apellido"), "Prueba");
    await user.type(
      screen.getByLabelText("Contraseña"),
      "synthetic_password_12",
    );
    await user.type(
      screen.getByLabelText("Confirma la contraseña"),
      "synthetic_password_12",
    );
    await user.click(
      screen.getByRole("button", { name: "Aceptar invitación" }),
    );

    expect(
      await screen.findByText(
        "El enlace de invitación no está disponible. Solicita a administración que envíe uno nuevo.",
      ),
    ).toBeVisible();
    expect(runtime.getSnapshot()).toEqual({ kind: "empty" });
    expect(submissions).toHaveBeenCalledTimes(1);
    expect(screen.queryByLabelText("Contraseña")).toBeNull();
  }, 15_000);

  it("does not replay an uncertain acceptance and directs the user to manual recovery", async () => {
    const user = userEvent.setup();
    const submissions = vi.fn();
    serveAnonymousSession();
    server.use(
      http.post("/api/v1/staff-invitations/inspect", () =>
        HttpResponse.json(preview("RECEPTIONIST", "BRANCH", [branch])),
      ),
      http.post("/api/v1/staff-invitations/accept", () => {
        submissions();
        return HttpResponse.json(
          { status: 500, code: "INTERNAL_ERROR" },
          { status: 500 },
        );
      }),
    );
    const runtime = createPublicIdentityRuntime({
      kind: "available",
      purpose: "invitation",
      token: invitationToken,
    });

    renderApplication("/accept-invitation", runtime);
    await screen.findByRole("heading", { name: "Aceptar invitación" });
    await user.click(
      screen.getByRole("button", { name: "Revisar invitación" }),
    );
    await screen.findByText("g***@example.test");
    await user.type(screen.getByLabelText("Nombre"), "Ana");
    await user.type(screen.getByLabelText("Apellido"), "Prueba");
    await user.type(
      screen.getByLabelText("Contraseña"),
      "synthetic_password_12",
    );
    await user.type(
      screen.getByLabelText("Confirma la contraseña"),
      "synthetic_password_12",
    );
    await user.click(
      screen.getByRole("button", { name: "Aceptar invitación" }),
    );

    expect(
      await screen.findByText(
        /No pudimos confirmar si la aceptación se completó/,
      ),
    ).toBeVisible();
    expect(runtime.getSnapshot()).toEqual({ kind: "empty" });
    expect(submissions).toHaveBeenCalledTimes(1);
    expect(screen.queryByLabelText("Contraseña")).toBeNull();
    expect(
      screen.getByRole("link", { name: "Volver a iniciar sesión" }),
    ).toHaveAttribute("href", "/login");
  }, 15_000);

  it("requires explicit logout before it processes an invitation in an open session", async () => {
    const inspect = vi.fn();
    server.use(
      http.get("/api/v1/auth/me", () =>
        HttpResponse.json({
          ...identityFixture,
          passwordChangeRequired: false,
        }),
      ),
      http.get("/api/v1/auth/csrf", () => csrfResponse()),
      http.get("/api/v1/me/profile", () =>
        HttpResponse.json({
          userId: identityFixture.id,
          username: identityFixture.username,
          email: "staff@example.test",
          firstName: "Ana",
          lastName: "Ríos",
          displayName: "Ana Ríos",
          roles: identityFixture.roles,
          status: "ACTIVE",
          photoPresent: false,
          photoUrl: null,
          version: 1,
        }),
      ),
      http.post("/api/v1/staff-invitations/inspect", () => {
        inspect();
        return HttpResponse.json(preview("ADMIN", "ORGANIZATION"));
      }),
    );
    const runtime = createPublicIdentityRuntime({
      kind: "available",
      purpose: "invitation",
      token: invitationToken,
    });

    renderApplication("/accept-invitation", runtime);

    expect(
      await screen.findByRole("button", { name: "Cerrar sesión y continuar" }),
    ).toBeVisible();
    expect(inspect).not.toHaveBeenCalled();
    expect(runtime.getSnapshot()).toMatchObject({
      kind: "available",
      purpose: "invitation",
    });
  });
});
