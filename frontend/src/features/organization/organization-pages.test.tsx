import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { RouterProvider } from "react-router";
import { describe, expect, it, vi } from "vitest";
import type { CurrentUser } from "../../shared/api/contracts";
import { AppProviders } from "../../app/AppProviders";
import { createMemoryAppRouter } from "../../app/router/AppRoutes";
import { branchFixture, identityFixture } from "../../test/identity-fixtures";
import { server } from "../../test/server";

const branchId = "00000000-0000-4000-8000-000000000020";

function session(overrides: Partial<CurrentUser> = {}): CurrentUser {
  return structuredClone({
    ...identityFixture,
    availableBranches: [branchFixture],
    activeBranch: branchFixture,
    ...overrides,
  }) as CurrentUser;
}

function branchRecord(overrides: Record<string, unknown> = {}) {
  return {
    id: branchId,
    code: "NORTH",
    name: "Sucursal Norte",
    addressLine1: "Avenida Central 12",
    addressLine2: null,
    city: "San Salvador",
    stateOrDepartment: "San Salvador",
    postalCode: "1101",
    countryCode: "SV",
    phone: null,
    email: null,
    timezone: "America/El_Salvador",
    status: "INACTIVE",
    initialBranch: false,
    version: 3,
    updatedAt: "2026-10-31T18:30:00Z",
    ...overrides,
  };
}

function installSession(user: CurrentUser) {
  server.use(
    http.get("/api/v1/auth/me", () => HttpResponse.json(user)),
    http.get("/api/v1/me/profile", () =>
      HttpResponse.json({
        userId: user.id,
        username: user.username,
        email: "operator@example.test",
        firstName: "Ana",
        lastName: "Ríos",
        displayName: user.fullName,
        roles: user.roles,
        status: "ACTIVE",
        photoPresent: false,
        photoUrl: null,
        version: 1,
      }),
    ),
  );
}

function renderApp(path: string) {
  Object.defineProperty(document, "visibilityState", {
    configurable: true,
    value: "visible",
  });
  const router = createMemoryAppRouter([path]);
  return render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  );
}

describe("organization and branch read pages", () => {
  it("shows only the organization projection returned to a receptionist", async () => {
    const receptionist = session({
      roles: ["RECEPTIONIST"],
      organizationScope: "BRANCH",
    });
    installSession(receptionist);
    server.use(
      http.get("/api/v1/organization", () =>
        HttpResponse.json({
          id: "00000000-0000-4000-8000-000000000001",
          code: "COACH",
          brandName: "Coach Gym",
          defaultTimezone: "America/El_Salvador",
          defaultCurrency: "USD",
          status: "ACTIVE",
        }),
      ),
    );

    renderApp("/organizacion");
    expect(await screen.findByText("USD")).toBeVisible();
    expect(
      document.querySelector(".cg-organization-card .ant-descriptions-title"),
    ).toHaveTextContent("Coach Gym");
    expect(screen.queryByText("Razón social")).not.toBeInTheDocument();
    expect(screen.queryByText("Correo de soporte")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sucursales" })).toBeVisible();
  });

  it("applies URL-backed catalog filters and returns from an inactive detail safely", async () => {
    const user = userEvent.setup();
    const catalogRequests: URL[] = [];
    installSession(
      session({
        roles: ["ADMIN"],
        organizationScope: "BRANCH",
      }),
    );
    server.use(
      http.get("/api/v1/branches", ({ request }) => {
        catalogRequests.push(new URL(request.url));
        return HttpResponse.json({
          items: [branchRecord()],
          page: Number(new URL(request.url).searchParams.get("page") ?? "0"),
          size: Number(new URL(request.url).searchParams.get("size") ?? "25"),
          totalElements: 1,
          totalPages: 1,
        });
      }),
      http.get(`/api/v1/branches/${branchId}`, () =>
        HttpResponse.json(branchRecord()),
      ),
    );

    renderApp("/sucursales");
    await screen.findByRole("heading", { name: "Sucursales" });
    await waitFor(() => expect(catalogRequests.length).toBeGreaterThan(0));
    expect(
      await screen.findByRole("link", {
        name: "Ver detalle de Sucursal Norte",
      }),
    ).toBeVisible();
    expect(
      screen.queryByRole("button", { name: /nueva sucursal/i }),
    ).not.toBeInTheDocument();

    await user.type(
      screen.getByLabelText("Buscar por código, nombre o ciudad"),
      "North",
    );
    await user.click(screen.getByRole("button", { name: "Aplicar filtros" }));
    await waitFor(() => {
      expect(catalogRequests.at(-1)?.searchParams.get("search")).toBe("North");
    });
    await user.click(
      screen.getByRole("link", {
        name: "Ver detalle de Sucursal Norte",
      }),
    );
    expect(
      await screen.findByRole("heading", { name: "Sucursal Norte" }),
    ).toBeVisible();
    expect(screen.getByText(/está inactiva/i)).toBeVisible();
    expect(screen.getByText(/no concede permisos para operar/i)).toBeVisible();
    await user.click(screen.getByRole("link", { name: "Volver al catálogo" }));
    expect(
      await screen.findByRole("heading", { name: "Sucursales" }),
    ).toBeVisible();
    await waitFor(() =>
      expect(catalogRequests.at(-1)?.searchParams.get("search")).toBe("North"),
    );
  }, 10_000);

  it("rejects malformed URL filters without requesting the catalog", async () => {
    installSession(session());
    const catalogReads = vi.fn();
    server.use(
      http.get("/api/v1/branches", () => {
        catalogReads();
        return HttpResponse.json({
          items: [],
          page: 0,
          size: 25,
          totalElements: 0,
          totalPages: 0,
        });
      }),
    );

    renderApp("/sucursales?page=-1");

    expect(
      await screen.findByText("La paginación de la URL no es válida."),
    ).toBeVisible();
    expect(catalogReads).not.toHaveBeenCalled();
  });

  it("does not read organization data without a server session", async () => {
    let organizationReads = 0;
    server.use(
      http.get("/api/v1/auth/me", () =>
        HttpResponse.json(
          { status: 401, code: "AUTHENTICATION_REQUIRED" },
          { status: 401 },
        ),
      ),
      http.get("/api/v1/organization", () => {
        organizationReads += 1;
        return HttpResponse.json({});
      }),
    );

    renderApp("/organizacion");

    expect(
      await screen.findByRole("heading", { name: "Iniciar sesión" }),
    ).toBeVisible();
    expect(organizationReads).toBe(0);
  });
});
