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

const branchB = {
  ...branchFixture,
  id: "00000000-0000-4000-8000-000000000004",
  code: "SECOND",
  name: "Sucursal norte",
  initialBranch: false,
};

type SessionOptions = Partial<CurrentUser>;

function session(overrides: SessionOptions = {}): CurrentUser {
  return structuredClone({
    ...identityFixture,
    availableBranches: [branchFixture, branchB],
    activeBranch: branchFixture,
    ...overrides,
  }) as CurrentUser;
}

function profileResponse(identity: CurrentUser) {
  return {
    userId: identity.id,
    username: identity.username,
    email: "operator@example.test",
    firstName: "Ana",
    lastName: "Ríos",
    displayName: identity.fullName,
    roles: identity.roles,
    status: "ACTIVE",
    photoPresent: false,
    photoUrl: null,
    version: 4,
  };
}

function renderApp(path = "/") {
  const router = createMemoryAppRouter([path]);
  return render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  );
}

function installSession(initialUser: CurrentUser) {
  let currentUser = initialUser;
  const identityReads = vi.fn();
  const catalogReads = vi.fn();
  const selectRequests = vi.fn();
  const clearRequests = vi.fn();
  let csrfRequests = 0;

  server.use(
    http.get("/api/v1/auth/me", () => {
      identityReads(currentUser.activeBranch?.id ?? null);
      return HttpResponse.json(currentUser);
    }),
    http.get("/api/v1/me/profile", () =>
      HttpResponse.json(profileResponse(currentUser)),
    ),
    http.get("/api/v1/auth/csrf", () => {
      csrfRequests += 1;
      return HttpResponse.json({
        token: "synthetic-branch-context-csrf",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      });
    }),
    http.get("/api/v1/branches", () => {
      catalogReads();
      return HttpResponse.json(
        { status: 500, code: "UNEXPECTED_BRANCH_CATALOG_READ" },
        { status: 500 },
      );
    }),
    http.put("/api/v1/me/branch-context", async ({ request }) => {
      selectRequests(await request.json());
      expect(request.headers.get("X-XSRF-TOKEN")).toBe(
        "synthetic-branch-context-csrf",
      );
      const body = selectRequests.mock.calls.at(-1)?.[0] as {
        branchId: string;
      };
      const selected = currentUser.availableBranches.find(
        ({ id }) => id === body.branchId,
      );
      if (!selected) {
        return HttpResponse.json(
          { status: 409, code: "ACTIVE_BRANCH_UNAVAILABLE" },
          { status: 409 },
        );
      }
      currentUser = { ...currentUser, activeBranch: selected };
      return HttpResponse.json({
        organizationId: selected.organizationId,
        scopeType: currentUser.organizationScope,
        activeBranch: selected,
        availableBranches: currentUser.availableBranches,
      });
    }),
    http.delete("/api/v1/me/branch-context", async ({ request }) => {
      const body = await request.text();
      clearRequests({
        method: request.method,
        query: new URL(request.url).search,
        body,
        csrf: request.headers.get("X-XSRF-TOKEN"),
      });
      currentUser = {
        ...currentUser,
        activeBranch:
          currentUser.availableBranches.length === 1
            ? (currentUser.availableBranches[0] ?? null)
            : null,
      };
      return new HttpResponse(null, { status: 204 });
    }),
  );

  return {
    identityReads,
    catalogReads,
    selectRequests,
    clearRequests,
    csrfRequests: () => csrfRequests,
    getCurrentUser: () => currentUser,
  };
}

async function headerControl() {
  const header = await waitFor(() => {
    const element = document.querySelector(
      '[data-branch-context-control="header"]',
    );
    if (!element) throw new Error("The authenticated header is not ready.");
    return element;
  });
  return within(header as HTMLElement);
}

describe("F03 branch context controls", () => {
  it.each([
    ["ADMIN", "ORGANIZATION"],
    ["ADMIN", "BRANCH"],
    ["RECEPTIONIST", "BRANCH"],
  ] as const)(
    "%s with %s scope can select only a server-authorized branch",
    async (role, scope) => {
      const user = userEvent.setup();
      const currentUser = session({
        roles: [role],
        organizationScope: scope,
      });
      const api = installSession(currentUser);
      renderApp();

      const control = await headerControl();
      const selector = await control.findByRole("combobox", {
        name: "Sucursal operativa",
      });
      await user.click(selector);
      await waitFor(() =>
        expect(selector).toHaveAttribute("aria-expanded", "true"),
      );
      expect(
        screen.queryByRole("option", { name: /todas las sucursales/i }),
      ).not.toBeInTheDocument();
      expect(api.catalogReads).not.toHaveBeenCalled();

      await user.click(
        await screen.findByRole("option", { name: "Sucursal norte · SECOND" }),
      );

      expect(await screen.findByText("Sucursal: Sucursal norte")).toBeVisible();
      expect(api.selectRequests).toHaveBeenCalledTimes(1);
      expect(api.selectRequests).toHaveBeenCalledWith({ branchId: branchB.id });
      expect(api.getCurrentUser().activeBranch?.id).toBe(branchB.id);
      expect(api.csrfRequests()).toBe(1);
    },
  );

  it("does not choose a default branch for an organization with no active selection", async () => {
    const api = installSession(
      session({
        activeBranch: null,
        availableBranches: [branchFixture, branchB],
      }),
    );
    renderApp();

    const control = await headerControl();
    expect(
      await control.findByRole("group", {
        name: "Ámbito de organización · Sin sucursal activa",
      }),
    ).toBeVisible();
    expect(await control.findByText("Sin sucursal activa")).toBeVisible();
    expect(api.selectRequests).not.toHaveBeenCalled();
    expect(api.catalogReads).not.toHaveBeenCalled();
  });

  it("clears only a useful multi-branch preference with CSRF", async () => {
    const user = userEvent.setup();
    const api = installSession(
      session({
        activeBranch: branchFixture,
        availableBranches: [branchFixture, branchB],
      }),
    );
    renderApp();

    const control = await headerControl();
    await user.click(
      control.getByRole("button", {
        name: "Quitar selección",
      }),
    );

    await waitFor(() => expect(api.clearRequests).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(api.getCurrentUser().activeBranch).toBeNull());
    await waitFor(() =>
      expect(
        api.identityReads.mock.calls.some(
          ([activeBranchId]) => activeBranchId === null,
        ),
      ).toBe(true),
    );
    expect(api.clearRequests).toHaveBeenCalledWith({
      method: "DELETE",
      query: "",
      body: "",
      csrf: "synthetic-branch-context-csrf",
    });
    expect(api.getCurrentUser().activeBranch).toBeNull();
  });

  it("keeps the server's one-branch automatic selection and hides the ineffective clear action", async () => {
    const currentUser = session({
      organizationScope: "BRANCH",
      roles: ["RECEPTIONIST"],
      activeBranch: branchFixture,
      availableBranches: [branchFixture],
    });
    const api = installSession(currentUser);
    renderApp();

    const control = await headerControl();
    const selector = await control.findByRole("combobox", {
      name: "Sucursal operativa",
    });
    expect(await control.findByText("Sucursal de prueba · TEST")).toBeVisible();
    expect(selector).toBeDisabled();
    expect(
      control.queryByRole("button", {
        name: "Quitar selección",
      }),
    ).not.toBeInTheDocument();
    expect(api.clearRequests).not.toHaveBeenCalled();
  });

  it("shows a no-branches organization state without inventing an all-branches option", async () => {
    const api = installSession(
      session({ activeBranch: null, availableBranches: [] }),
    );
    renderApp();

    const control = await headerControl();
    expect(
      await control.findByText(
        "Ámbito de organización · Sin sucursales operativas disponibles",
      ),
    ).toBeVisible();
    expect(
      control.queryByRole("combobox", {
        name: "Sucursal operativa",
      }),
    ).not.toBeInTheDocument();
    expect(api.catalogReads).not.toHaveBeenCalled();
  });

  it("blocks selection from the real header while the F02 profile draft is dirty", async () => {
    const user = userEvent.setup();
    const api = installSession(
      session({
        activeBranch: branchFixture,
        availableBranches: [branchFixture, branchB],
      }),
    );
    renderApp("/mi-perfil");

    const firstName = await screen.findByLabelText("Nombre");
    await user.clear(firstName);
    await user.type(firstName, "Ana María");
    const control = await headerControl();
    await user.click(
      control.getByRole("combobox", {
        name: "Sucursal operativa",
      }),
    );
    await waitFor(() =>
      expect(
        control.getByRole("combobox", { name: "Sucursal operativa" }),
      ).toHaveAttribute("aria-expanded", "true"),
    );
    await user.click(
      await screen.findByRole("option", { name: "Sucursal norte · SECOND" }),
    );

    await waitFor(() =>
      expect(
        screen.getByText(
          "Guarda o descarta los cambios del perfil, la foto o la sucursal antes de cambiar de sucursal.",
        ),
      ).toBeVisible(),
    );
    expect(api.selectRequests).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Nombre")).toHaveValue("Ana María");
  }, 10_000);
});
