import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { RouterProvider } from "react-router";
import { describe, expect, it, vi } from "vitest";
import type { CurrentUser } from "../../shared/api/contracts";
import { AppProviders } from "../../app/AppProviders";
import { createMemoryAppRouter } from "../../app/router/AppRoutes";
import { branchFixture, identityFixture } from "../../test/identity-fixtures";
import { server } from "../../test/server";

const branchId = branchFixture.id;
const otherBranch = {
  ...branchFixture,
  id: "00000000-0000-4000-8000-000000000004",
  code: "SOUTH",
  name: "Sucursal Sur",
  initialBranch: false,
};
const managedBranch = { ...branchFixture, initialBranch: false };

function branchRecord(overrides: Record<string, unknown> = {}) {
  return {
    id: branchId,
    code: "TEST",
    name: "Sucursal de prueba",
    addressLine1: null,
    addressLine2: null,
    city: "San Salvador",
    stateOrDepartment: null,
    postalCode: null,
    countryCode: "SV",
    phone: null,
    email: null,
    timezone: "America/El_Salvador",
    status: "ACTIVE",
    initialBranch: false,
    version: 7,
    updatedAt: "2026-11-01T16:00:00Z",
    ...overrides,
  };
}

function session(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return structuredClone({
    ...identityFixture,
    activeBranch: managedBranch,
    availableBranches: [managedBranch, otherBranch],
    ...overrides,
  }) as CurrentUser;
}

function installSession(initial: CurrentUser) {
  let currentUser = initial;
  server.use(
    http.get("/api/v1/auth/me", () => HttpResponse.json(currentUser)),
    http.get("/api/v1/me/profile", () =>
      HttpResponse.json({
        userId: currentUser.id,
        username: currentUser.username,
        email: "operator@example.test",
        firstName: "Ana",
        lastName: "Ríos",
        displayName: currentUser.fullName,
        roles: currentUser.roles,
        status: "ACTIVE",
        photoPresent: false,
        photoUrl: null,
        version: 2,
      }),
    ),
    http.get("/api/v1/auth/csrf", () =>
      HttpResponse.json({
        token: "synthetic-branch-status-csrf",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      }),
    ),
  );
  return {
    getCurrentUser: () => currentUser,
    setCurrentUser: (next: CurrentUser) => {
      currentUser = next;
    },
  };
}

function renderDetail(user: CurrentUser) {
  Object.defineProperty(document, "visibilityState", {
    configurable: true,
    value: "visible",
  });
  const state = installSession(user);
  const router = createMemoryAppRouter([`/sucursales/${branchId}`]);
  const view = render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...view, ...state };
}

async function confirmStatus(
  user: ReturnType<typeof userEvent.setup>,
  target: "ACTIVE" | "INACTIVE",
  reason = "Cambio operativo aprobado",
  beforeConfirm?: (dialog: HTMLElement) => void | Promise<void>,
) {
  const buttonName =
    target === "ACTIVE" ? "Activar sucursal" : "Desactivar sucursal";
  await user.click(await screen.findByRole("button", { name: buttonName }));
  const dialog = await screen.findByRole("dialog", {
    name: `${target === "ACTIVE" ? "Activar" : "Desactivar"} sucursal: Sucursal de prueba`,
  });
  if (reason) {
    await user.type(
      within(dialog).getByRole("textbox", { name: "Motivo requerido" }),
      reason,
    );
  }
  await beforeConfirm?.(dialog);
  await user.click(
    within(dialog).getByRole("button", {
      name:
        target === "ACTIVE"
          ? "Confirmar activación"
          : "Confirmar desactivación",
    }),
  );
  return dialog;
}

describe("B07 branch lifecycle flow", () => {
  it("sends the fresh version with CSRF and reconciles a deactivated active branch", async () => {
    const user = userEvent.setup();
    const state = renderDetail(session());
    let branch = branchRecord();
    let submittedBody: unknown;
    let csrfHeader: string | null = null;
    let requests = 0;
    server.use(
      http.get(`/api/v1/branches/${branchId}`, () => HttpResponse.json(branch)),
      http.post(
        `/api/v1/branches/${branchId}/deactivate`,
        async ({ request }) => {
          requests += 1;
          submittedBody = await request.json();
          csrfHeader = request.headers.get("X-XSRF-TOKEN");
          branch = branchRecord({ status: "INACTIVE", version: 8 });
          state.setCurrentUser({
            ...state.getCurrentUser(),
            activeBranch: otherBranch,
            availableBranches: [otherBranch],
          });
          return HttpResponse.json(branch);
        },
      ),
    );

    await screen.findByRole("heading", { name: "Sucursal de prueba" });
    await confirmStatus(
      user,
      "INACTIVE",
      "Cambio operativo aprobado",
      (dialog) => {
        expect(
          within(dialog).getByText(/Esta es tu sucursal operativa/),
        ).toBeInTheDocument();
      },
    );
    await waitFor(() =>
      expect(screen.getByText("Estado actualizado")).toBeVisible(),
    );

    expect(requests).toBe(1);
    expect(submittedBody).toEqual({
      reason: "Cambio operativo aprobado",
      version: 7,
    });
    expect(csrfHeader).toBe("synthetic-branch-status-csrf");
    expect(state.getCurrentUser().activeBranch?.id).toBe(otherBranch.id);
    expect(await screen.findByText("Sucursal Sur · SOUTH")).toBeVisible();
    expect(screen.getByText("Estado actualizado")).toBeVisible();
    expect(
      screen.queryByText("Cambio operativo aprobado"),
    ).not.toBeInTheDocument();
  });

  it("activates an inactive branch with the canonical version and response", async () => {
    const user = userEvent.setup();
    const state = renderDetail(
      session({ activeBranch: otherBranch, availableBranches: [otherBranch] }),
    );
    let branch = branchRecord({ status: "INACTIVE", version: 4 });
    let submittedBody: unknown;
    let requests = 0;
    server.use(
      http.get(`/api/v1/branches/${branchId}`, () => HttpResponse.json(branch)),
      http.post(
        `/api/v1/branches/${branchId}/activate`,
        async ({ request }) => {
          requests += 1;
          submittedBody = await request.json();
          branch = branchRecord({ status: "ACTIVE", version: 5 });
          state.setCurrentUser({
            ...state.getCurrentUser(),
            availableBranches: [managedBranch, otherBranch],
          });
          return HttpResponse.json(branch);
        },
      ),
    );

    await screen.findByRole("heading", { name: "Sucursal de prueba" });
    await confirmStatus(user, "ACTIVE");
    expect(await screen.findByText("Estado actualizado")).toBeVisible();
    expect(requests).toBe(1);
    expect(submittedBody).toEqual({
      reason: "Cambio operativo aprobado",
      version: 4,
    });
    expect(screen.getByText("Activa", { selector: ".ant-tag" })).toBeVisible();
    expect(
      screen.getByRole("button", { name: /Desactivar sucursal/ }),
    ).toBeEnabled();
  });

  it("lets the backend enforce the initial-branch invariant and requires a read after conflict", async () => {
    const user = userEvent.setup();
    renderDetail(session());
    let reads = 0;
    let requests = 0;
    server.use(
      http.get(`/api/v1/branches/${branchId}`, () => {
        reads += 1;
        return HttpResponse.json(branchRecord({ initialBranch: true }));
      }),
      http.post(`/api/v1/branches/${branchId}/deactivate`, () => {
        requests += 1;
        return HttpResponse.json(
          { status: 409, code: "GYM_BRANCH_INITIAL_PROTECTED" },
          {
            status: 409,
            headers: { "Content-Type": "application/problem+json" },
          },
        );
      }),
    );

    await screen.findByRole("heading", { name: "Sucursal de prueba" });
    await confirmStatus(
      user,
      "INACTIVE",
      "Cambio operativo aprobado",
      (dialog) => {
        expect(
          within(dialog).getByText(/El servidor rechazará la desactivación/),
        ).toBeInTheDocument();
      },
    );
    expect(await screen.findByText("La sucursal cambió")).toBeVisible();
    expect(requests).toBe(1);
    expect(reads).toBeGreaterThanOrEqual(2);
    const refreshButton = await screen.findByRole("button", {
      name: "Actualizar ficha",
    });
    await waitFor(() => expect(refreshButton).toBeEnabled());
    await user.click(refreshButton);
    await waitFor(() =>
      expect(screen.queryByText("La sucursal cambió")).not.toBeInTheDocument(),
    );
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: /Desactivar sucursal/ }),
      ).toBeEnabled(),
    );
  });

  it("requires a nonblank reason before sending any lifecycle request", async () => {
    const user = userEvent.setup();
    renderDetail(session());
    const requests = vi.fn();
    server.use(
      http.get(`/api/v1/branches/${branchId}`, () =>
        HttpResponse.json(branchRecord()),
      ),
      http.post(`/api/v1/branches/${branchId}/deactivate`, () => {
        requests();
        return HttpResponse.json(branchRecord({ status: "INACTIVE" }));
      }),
    );

    await screen.findByRole("heading", { name: "Sucursal de prueba" });
    const dialog = await confirmStatus(user, "INACTIVE", "");
    expect(
      within(dialog).getByText("Escribe un motivo para continuar."),
    ).toHaveAttribute("role", "alert");
    expect(requests).not.toHaveBeenCalled();
  });

  it("does not repeat a CSRF-rejected lifecycle action", async () => {
    const user = userEvent.setup();
    renderDetail(session());
    const requests = vi.fn();
    server.use(
      http.get(`/api/v1/branches/${branchId}`, () =>
        HttpResponse.json(branchRecord()),
      ),
      http.post(`/api/v1/branches/${branchId}/deactivate`, () => {
        requests();
        return HttpResponse.json(
          { status: 403, code: "CSRF_TOKEN_INVALID" },
          {
            status: 403,
            headers: { "Content-Type": "application/problem+json" },
          },
        );
      }),
    );

    await screen.findByRole("heading", { name: "Sucursal de prueba" });
    await confirmStatus(user, "INACTIVE");
    expect(
      await screen.findByText("No se pudo cambiar el estado"),
    ).toBeVisible();
    expect(
      screen.getByText(
        "La protección de la solicitud cambió. Inténtalo de nuevo.",
      ),
    ).toBeVisible();
    expect(requests).toHaveBeenCalledTimes(1);
    expect(
      screen.queryByText("Cambio operativo aprobado"),
    ).not.toBeInTheDocument();
  });

  it("never repeats an uncertain status write and clears it only after an explicit read", async () => {
    const user = userEvent.setup();
    const state = renderDetail(session());
    let branch = branchRecord();
    let requests = 0;
    let reads = 0;
    server.use(
      http.get(`/api/v1/branches/${branchId}`, () => {
        reads += 1;
        return HttpResponse.json(branch);
      }),
      http.post(`/api/v1/branches/${branchId}/deactivate`, () => {
        requests += 1;
        branch = branchRecord({ status: "INACTIVE", version: 8 });
        state.setCurrentUser({
          ...state.getCurrentUser(),
          activeBranch: otherBranch,
          availableBranches: [otherBranch],
        });
        return HttpResponse.json(
          { status: 500, code: "GYM_BRANCH_DATA_ACCESS_FAILED" },
          {
            status: 500,
            headers: { "Content-Type": "application/problem+json" },
          },
        );
      }),
    );

    await screen.findByRole("heading", { name: "Sucursal de prueba" });
    await confirmStatus(user, "INACTIVE");
    expect(
      await screen.findByText("Resultado pendiente de comprobar"),
    ).toBeVisible();
    expect(requests).toBe(1);
    expect(reads).toBe(2);
    const lifecycleButtons = screen
      .getAllByRole("button")
      .filter((button) => /sucursal/i.test(button.textContent ?? ""));
    expect(
      lifecycleButtons.map((button) => button.textContent?.trim()),
    ).toContain("Desactivar sucursal");
    expect(lifecycleButtons[0]).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Comprobar estado" }));
    expect(
      await screen.findByText(
        /Lectura confirmada: la sucursal aparece inactiva/,
      ),
    ).toBeVisible();
    expect(requests).toBe(1);
    expect(await screen.findByText("Estado actualizado")).toBeVisible();
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: /Activar sucursal/ }),
      ).toBeEnabled(),
    );
  });

  it("does not expose lifecycle actions to an ADMIN scoped to a branch", async () => {
    const branchAdmin = session({ organizationScope: "BRANCH" });
    renderDetail(branchAdmin);
    server.use(
      http.get(`/api/v1/branches/${branchId}`, () =>
        HttpResponse.json(branchRecord()),
      ),
    );

    await screen.findByRole("heading", { name: "Sucursal de prueba" });
    expect(
      screen.queryByRole("button", { name: "Desactivar sucursal" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Activar sucursal" }),
    ).not.toBeInTheDocument();
  });
});
