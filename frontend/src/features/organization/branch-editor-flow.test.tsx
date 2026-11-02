import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { RouterProvider } from "react-router";
import { describe, expect, it } from "vitest";
import type { CurrentUser } from "../../shared/api/contracts";
import { AppProviders } from "../../app/AppProviders";
import { createMemoryAppRouter } from "../../app/router/AppRoutes";
import { branchFixture, identityFixture } from "../../test/identity-fixtures";
import { server } from "../../test/server";

const branchId = "00000000-0000-4000-8000-000000000020";
const createdBranchId = "00000000-0000-4000-8000-000000000021";

function session(
  role: "ADMIN" | "RECEPTIONIST" = "ADMIN",
  scope: "ORGANIZATION" | "BRANCH" = "ORGANIZATION",
): CurrentUser {
  return structuredClone({
    ...identityFixture,
    roles: [role],
    organizationScope: scope,
    activeBranch: scope === "BRANCH" ? branchFixture : null,
    availableBranches: [branchFixture],
    passwordChangeRequired: false,
  }) as CurrentUser;
}

function branchRecord(
  overrides: Record<string, unknown> = {},
): Record<string, unknown> {
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
    status: "ACTIVE",
    initialBranch: false,
    version: 7,
    updatedAt: "2026-11-01T16:00:00Z",
    ...overrides,
  };
}

function installSession(user: CurrentUser) {
  server.use(
    http.get("/api/v1/auth/me", () => HttpResponse.json(user)),
    http.get("/api/v1/me/branch-context", () =>
      HttpResponse.json({
        organizationId: branchFixture.organizationId,
        scopeType: user.organizationScope,
        activeBranch: user.activeBranch,
        availableBranches: user.availableBranches,
      }),
    ),
    http.get("/api/v1/auth/csrf", () =>
      HttpResponse.json({
        token: "synthetic-branch-editor-csrf",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      }),
    ),
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
        version: 2,
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
  const view = render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  );
  return { ...view, router };
}

describe("branch creation and editing", () => {
  it("gates direct editor navigation before reading or writing for ADMIN BRANCH", async () => {
    const currentUser = session("ADMIN", "BRANCH");
    installSession(currentUser);
    let branchRequests = 0;
    let writes = 0;
    server.use(
      http.get("/api/v1/branches/:branchId", () => {
        branchRequests += 1;
        return HttpResponse.json(branchRecord());
      }),
      http.post("/api/v1/branches", () => {
        writes += 1;
        return HttpResponse.json(branchRecord(), { status: 201 });
      }),
    );

    renderApp(`/sucursales/${branchId}/editar`);
    expect(await screen.findByText(/requiere una cuenta ADMIN/i)).toBeVisible();
    expect(branchRequests).toBe(0);
    expect(writes).toBe(0);
  });

  it("creates only on explicit submit with normalized allowlisted values", async () => {
    const user = userEvent.setup();
    installSession(session());
    let requests = 0;
    let requestBody: Record<string, unknown> | undefined;
    server.use(
      http.post("/api/v1/branches", async ({ request }) => {
        requests += 1;
        requestBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          branchRecord({
            id: createdBranchId,
            code: "CENTRAL_NORTH",
            name: "Gimnasio Norte",
            addressLine1: null,
            addressLine2: null,
            city: null,
            stateOrDepartment: null,
            postalCode: null,
            countryCode: null,
            phone: null,
            email: null,
            timezone: "America/El_Salvador",
            version: 0,
          }),
          {
            status: 201,
            headers: { Location: "https://example.invalid/unsafe" },
          },
        );
      }),
      http.get(`/api/v1/branches/${createdBranchId}`, () =>
        HttpResponse.json(
          branchRecord({
            id: createdBranchId,
            code: "CENTRAL_NORTH",
            name: "Gimnasio Norte",
            version: 0,
          }),
        ),
      ),
    );

    renderApp("/sucursales/nueva");
    expect(
      await screen.findByRole("heading", { name: "Nueva sucursal" }),
    ).toBeVisible();
    await waitFor(() => expect(requests).toBe(0));
    await user.type(
      await screen.findByLabelText("Código de la sucursal"),
      "central_north",
    );
    await user.type(
      await screen.findByLabelText("Nombre de la sucursal"),
      "Gimnasio   Norte",
    );
    await user.click(screen.getByRole("button", { name: "Crear sucursal" }));

    expect(
      await screen.findByRole("heading", { name: "Gimnasio Norte" }),
    ).toBeVisible();
    expect(requests).toBe(1);
    expect(requestBody).toEqual({
      code: "CENTRAL_NORTH",
      name: "Gimnasio Norte",
    });
    expect(requestBody).not.toHaveProperty("status");
    expect(requestBody).not.toHaveProperty("initialBranch");
    expect(requestBody).not.toHaveProperty("organizationId");
    expect(window.location.pathname).not.toContain("unsafe");
  });

  it("keeps an edit draft and sends the server version without editable code", async () => {
    const user = userEvent.setup();
    installSession(session());
    let updateBody: Record<string, unknown> | undefined;
    server.use(
      http.get(`/api/v1/branches/${branchId}`, () =>
        HttpResponse.json(branchRecord()),
      ),
      http.put(`/api/v1/branches/${branchId}`, async ({ request }) => {
        updateBody = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(
          branchRecord({ name: "Sucursal Norte Actualizada", version: 8 }),
        );
      }),
    );

    renderApp(`/sucursales/${branchId}/editar`);
    const name = await screen.findByLabelText("Nombre de la sucursal");
    expect(screen.getByText("Código:")).toHaveTextContent("NORTH");
    await user.clear(name);
    await user.type(name, "Sucursal Norte Actualizada");
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(
      await screen.findByRole("heading", {
        name: "Sucursal Norte Actualizada",
      }),
    ).toBeVisible();
    expect(updateBody).toEqual({
      name: "Sucursal Norte Actualizada",
      addressLine1: "Avenida Central 12",
      city: "San Salvador",
      stateOrDepartment: "San Salvador",
      postalCode: "1101",
      countryCode: "SV",
      timezone: "America/El_Salvador",
      version: 7,
    });
    expect(updateBody).not.toHaveProperty("code");
    expect(updateBody).not.toHaveProperty("status");
    expect(updateBody).not.toHaveProperty("initialBranch");
  });

  it("requires an explicit read and confirmation before replacing a version-conflicted draft", async () => {
    const user = userEvent.setup();
    installSession(session());
    let reads = 0;
    server.use(
      http.get(`/api/v1/branches/${branchId}`, () => {
        reads += 1;
        return HttpResponse.json(
          branchRecord({
            name: reads > 1 ? "Nombre del servidor" : "Sucursal Norte",
            version: reads > 1 ? 8 : 7,
          }),
        );
      }),
      http.put(`/api/v1/branches/${branchId}`, () =>
        HttpResponse.json(
          { status: 409, code: "GYM_BRANCH_VERSION_CONFLICT" },
          {
            status: 409,
            headers: { "Content-Type": "application/problem+json" },
          },
        ),
      ),
    );

    renderApp(`/sucursales/${branchId}/editar`);
    const name = await screen.findByLabelText("Nombre de la sucursal");
    await user.clear(name);
    await user.type(name, "Borrador conservado");
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));
    expect(
      await screen.findByText(/tus cambios se conservaron/i),
    ).toBeVisible();
    expect(name).toHaveValue("Borrador conservado");
    const saveButton = await screen.findByRole("button", {
      name: /Guardar cambios/,
    });
    await waitFor(() => expect(saveButton).not.toHaveClass("ant-btn-loading"));

    await user.click(
      screen.getByRole("button", { name: "Consultar versión actual" }),
    );
    expect(
      await screen.findByText(/Versión actual del servidor: 8/),
    ).toBeVisible();
    expect(name).toHaveValue("Borrador conservado");
    const useLatestButton = await screen.findByRole("button", {
      name: /Usar versión actual/,
    });
    await waitFor(() =>
      expect(useLatestButton).not.toHaveClass("ant-btn-loading"),
    );
    await user.click(useLatestButton);
    const replaceDialog = await screen.findByRole("dialog", {
      name: "Reemplazar el borrador",
    });
    expect(replaceDialog).toBeInTheDocument();
    fireEvent.click(
      within(replaceDialog).getByRole("button", {
        name: "Reemplazar con la versión actual",
      }),
    );
    await waitFor(() => expect(name).toHaveValue("Nombre del servidor"));
    expect(
      screen.getByText(/Versión base recibida del servidor: 8/),
    ).toBeVisible();
    const unchangedSaveButton = screen.getByRole("button", {
      name: /Guardar cambios/,
    });
    expect(unchangedSaveButton).not.toHaveClass("ant-btn-loading");
    expect(unchangedSaveButton).toBeDisabled();
  });

  it("does not repeat a write after an uncertain server response", async () => {
    const user = userEvent.setup();
    installSession(session());
    let requests = 0;
    server.use(
      http.post("/api/v1/branches", () => {
        requests += 1;
        return HttpResponse.json(
          { status: 500, code: "GYM_BRANCH_DATA_ACCESS_FAILED" },
          {
            status: 500,
            headers: { "Content-Type": "application/problem+json" },
          },
        );
      }),
    );

    renderApp("/sucursales/nueva");
    await user.type(
      await screen.findByLabelText("Código de la sucursal"),
      "NORTH_2",
    );
    await user.type(
      await screen.findByLabelText("Nombre de la sucursal"),
      "Sucursal Norte 2",
    );
    await user.click(screen.getByRole("button", { name: "Crear sucursal" }));
    expect(
      await screen.findByText(
        /No se pudo confirmar el resultado del guardado/i,
      ),
    ).toBeVisible();
    expect(requests).toBe(1);
    const createButton = await screen.findByRole("button", {
      name: /Crear sucursal/,
    });
    await waitFor(() =>
      expect(createButton).not.toHaveClass("ant-btn-loading"),
    );
    expect(createButton).toBeDisabled();
    expect(
      screen.getByRole("link", {
        name: "Comprobar en el catálogo en otra pestaña",
      }),
    ).toHaveAttribute("target", "_blank");
    expect(screen.getByLabelText("Nombre de la sucursal")).toHaveValue(
      "Sucursal Norte 2",
    );
  });

  it("does not read or write for reception", async () => {
    const receptionist = session("RECEPTIONIST", "BRANCH");
    installSession(receptionist);
    let reads = 0;
    let writes = 0;
    server.use(
      http.get(`/api/v1/branches/${branchId}`, () => {
        reads += 1;
        return HttpResponse.json(branchRecord());
      }),
      http.post("/api/v1/branches", () => {
        writes += 1;
        return HttpResponse.json(
          { status: 403, code: "ACCESS_DENIED" },
          {
            status: 403,
            headers: { "Content-Type": "application/problem+json" },
          },
        );
      }),
    );

    renderApp("/sucursales/nueva");
    expect(await screen.findByText(/requiere una cuenta ADMIN/i)).toBeVisible();
    expect(reads).toBe(0);
    expect(writes).toBe(0);
  });

  it("preserves the editor and current session after a server-side 403", async () => {
    const user = userEvent.setup();
    installSession(session());
    let meReads = 0;
    server.use(
      http.get("/api/v1/auth/me", () => {
        meReads += 1;
        return HttpResponse.json(session());
      }),
      http.post("/api/v1/branches", () =>
        HttpResponse.json(
          { status: 403, code: "ACCESS_DENIED" },
          {
            status: 403,
            headers: { "Content-Type": "application/problem+json" },
          },
        ),
      ),
    );

    renderApp("/sucursales/nueva");
    await user.type(
      await screen.findByLabelText("Código de la sucursal"),
      "NORTH_3",
    );
    await user.type(
      await screen.findByLabelText("Nombre de la sucursal"),
      "Sucursal Norte 3",
    );
    await user.click(screen.getByRole("button", { name: "Crear sucursal" }));

    expect(
      await screen.findByText("El servidor denegó la operación."),
    ).toBeVisible();
    expect(await screen.findByLabelText("Nombre de la sucursal")).toHaveValue(
      "Sucursal Norte 3",
    );
    expect(
      screen.getByRole("button", { name: "Crear sucursal" }),
    ).toBeEnabled();
    expect(meReads).toBeGreaterThanOrEqual(2);
  });
});
