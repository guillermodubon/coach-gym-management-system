import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { waitForVisualTransitions } from "../helpers/visual-stability";

const branchId = "00000000-0000-4000-8000-000000000020";
const createdBranchId = "00000000-0000-4000-8000-000000000021";
const organizationId = "00000000-0000-4000-8000-000000000001";

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

function identity(
  role: "ADMIN" | "RECEPTIONIST" = "ADMIN",
  scope: "ORGANIZATION" | "BRANCH" = "BRANCH",
) {
  const activeBranch = {
    id: "00000000-0000-4000-8000-000000000002",
    organizationId,
    code: "MAIN",
    name: "Sucursal Central",
    timezone: "America/El_Salvador",
    initialBranch: true,
  };
  return {
    id: "00000000-0000-4000-8000-000000000003",
    username: "operator-test",
    fullName: "Operador de prueba",
    roles: [role],
    organizationScope: scope,
    activeBranch: scope === "BRANCH" ? activeBranch : null,
    availableBranches: [activeBranch],
    passwordChangeRequired: false,
  };
}

async function installReadSession(
  page: Page,
  role: "ADMIN" | "RECEPTIONIST" = "ADMIN",
  scope: "ORGANIZATION" | "BRANCH" = "BRANCH",
) {
  const currentIdentity = identity(role, scope);
  await page.route("**/api/v1/auth/me", (route) =>
    route.fulfill({ status: 200, json: currentIdentity }),
  );
  await page.route("**/api/v1/me/profile", (route) =>
    route.fulfill({
      status: 200,
      json: {
        userId: currentIdentity.id,
        username: currentIdentity.username,
        email: "operator@example.test",
        firstName: "Operador",
        lastName: "Prueba",
        displayName: currentIdentity.fullName,
        roles: currentIdentity.roles,
        status: "ACTIVE",
        photoPresent: false,
        photoUrl: null,
        version: 1,
      },
    }),
  );
  await page.route("**/api/v1/me/branch-context", (route) =>
    route.fulfill({
      status: 200,
      json: {
        organizationId,
        scopeType: currentIdentity.organizationScope,
        activeBranch: currentIdentity.activeBranch,
        availableBranches: currentIdentity.availableBranches,
      },
    }),
  );
  await page.route("**/api/v1/organization", (route) =>
    route.fulfill({
      status: 200,
      json: {
        id: organizationId,
        code: "COACH",
        brandName: "Coach Gym",
        ...(role === "ADMIN"
          ? {
              legalName: "Coach Gym S.A.",
              supportEmail: "support@example.test",
              version: 5,
              updatedAt: "2026-10-31T18:30:00Z",
            }
          : {}),
        defaultTimezone: "America/El_Salvador",
        defaultCurrency: "USD",
        status: "ACTIVE",
      },
    }),
  );
  await page.route(
    (url) => url.pathname === "/api/v1/branches",
    (route) => {
      const url = new URL(route.request().url());
      const item = branchRecord();
      const matches =
        !url.searchParams.has("search") ||
        item.name
          .toLowerCase()
          .includes(url.searchParams.get("search")!.toLowerCase());
      return route.fulfill({
        status: 200,
        json: {
          items: matches ? [item] : [],
          page: Number(url.searchParams.get("page") ?? 0),
          size: Number(url.searchParams.get("size") ?? 25),
          totalElements: matches ? 1 : 0,
          totalPages: matches ? 1 : 0,
        },
      });
    },
  );
  await page.route(`**/api/v1/branches/${branchId}`, (route) =>
    route.fulfill({ status: 200, json: branchRecord() }),
  );
}

test("consults organization, filtered branch catalog, and inactive detail accessibly", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await installReadSession(page, "ADMIN");
  await page.goto("/organizacion");

  await expect(page).toHaveTitle("Organización · Coach Gym");
  await expect(
    page.getByRole("heading", { name: "Organización", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("Coach Gym S.A.", { exact: true })).toBeVisible();
  await waitForVisualTransitions(page);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

  await page
    .getByRole("navigation", { name: "Navegación principal" })
    .getByRole("link", { name: "Sucursales" })
    .click();
  await expect(page).toHaveURL(/\/sucursales$/);
  await expect(
    page.getByRole("heading", { name: "Sucursales", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("textbox", { name: "Buscar por código, nombre o ciudad" })
    .fill("Norte");
  await page.getByRole("button", { name: "Aplicar filtros" }).click();
  await expect(page).toHaveURL(/search=Norte/);
  await expect(
    page.getByRole("link", { name: "Ver detalle de Sucursal Norte" }),
  ).toBeVisible();
  await waitForVisualTransitions(page);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

  await page
    .getByRole("link", { name: "Ver detalle de Sucursal Norte" })
    .click();
  await expect(page).toHaveURL(new RegExp(`/sucursales/${branchId}$`));
  await expect(
    page.getByRole("heading", { name: "Sucursal Norte" }),
  ).toBeVisible();
  await expect(page.getByText(/está inactiva/i)).toBeVisible();
  await expect(
    page.getByText(/no concede permisos para operar/i),
  ).toBeVisible();
  await waitForVisualTransitions(page);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

  await page.goBack();
  await expect(page).toHaveURL(/\/sucursales\?search=Norte/);
  await expect(
    page.getByRole("heading", { name: "Sucursales", exact: true }),
  ).toBeVisible();
  await page.goForward();
  await expect(page).toHaveURL(new RegExp(`/sucursales/${branchId}$`));

  await page.setViewportSize({ width: 375, height: 812 });
  await expect(
    page.getByRole("heading", { name: "Sucursal Norte" }),
  ).toBeVisible();
  const widths = await page.evaluate(() => ({
    document: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
  }));
  expect(widths.document).toBeLessThanOrEqual(widths.viewport);
});

test("shows a safe access-denied state for organization read failures", async ({
  page,
}) => {
  await installReadSession(page, "RECEPTIONIST");
  await page.route("**/api/v1/organization", (route) =>
    route.fulfill({
      status: 403,
      contentType: "application/problem+json",
      body: JSON.stringify({ status: 403, code: "ACCESS_DENIED" }),
    }),
  );
  await page.goto("/organizacion");
  await expect(
    page.getByText(
      "Tu cuenta no tiene autorización para consultar esta información.",
    ),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Reintentar" })).toBeVisible();
});

test("organization admins create and edit branches with guarded, canonical writes", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await installReadSession(page, "ADMIN", "ORGANIZATION");
  await page.route("**/api/v1/auth/csrf", (route) =>
    route.fulfill({
      status: 200,
      json: {
        token: "synthetic-branch-editor-csrf",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      },
    }),
  );

  let createBody: Record<string, unknown> | undefined;
  let createRequests = 0;
  let updateBody: Record<string, unknown> | undefined;
  let createdBranch = branchRecord({
    id: createdBranchId,
    code: "E2E_NORTH",
    name: "Sucursal E2E",
    status: "ACTIVE",
    initialBranch: false,
    version: 0,
    addressLine1: null,
    addressLine2: null,
    city: null,
    stateOrDepartment: null,
    postalCode: null,
    countryCode: null,
    phone: null,
    email: null,
  });
  await page.route("**/api/v1/branches", async (route) => {
    if (route.request().method() === "POST") {
      createRequests += 1;
      createBody = route.request().postDataJSON() as Record<string, unknown>;
      expect(route.request().headers()["x-xsrf-token"]).toBe(
        "synthetic-branch-editor-csrf",
      );
      await route.fulfill({
        status: 201,
        json: createdBranch,
        headers: {
          Location: `http://127.0.0.1:8080/api/v1/branches/${createdBranchId}`,
        },
      });
      return;
    }
    await route.fulfill({
      status: 200,
      json: {
        items: [branchRecord()],
        page: 0,
        size: 25,
        totalElements: 1,
        totalPages: 1,
      },
    });
  });
  await page.route(`**/api/v1/branches/${createdBranchId}`, async (route) => {
    if (route.request().method() === "PUT") {
      updateBody = route.request().postDataJSON() as Record<string, unknown>;
      expect(route.request().headers()["x-xsrf-token"]).toBe(
        "synthetic-branch-editor-csrf",
      );
      createdBranch = {
        ...createdBranch,
        name: "Sucursal E2E actualizada",
        version: 1,
      };
      await route.fulfill({
        status: 200,
        json: createdBranch,
      });
      return;
    }
    await route.fulfill({
      status: 200,
      json: createdBranch,
    });
  });

  await page.goto("/sucursales");
  await page.getByRole("link", { name: "Nueva sucursal" }).click();
  await expect(page).toHaveURL(/\/sucursales\/nueva$/);
  await page
    .getByRole("textbox", { name: "Código de la sucursal" })
    .fill("e2e_north");
  await page
    .getByRole("textbox", { name: "Nombre de la sucursal" })
    .fill("Sucursal E2E");
  await expect(
    page.getByRole("button", { name: "Crear sucursal" }),
  ).toBeEnabled();
  expect(createRequests).toBe(0);
  await page.getByRole("button", { name: "Cancelar" }).click();
  await expect(
    page.getByRole("dialog", { name: "Tienes cambios sin guardar" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Seguir editando" }).click();
  await expect(
    page.getByRole("textbox", { name: "Código de la sucursal" }),
  ).toHaveValue("e2e_north");
  await waitForVisualTransitions(page);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

  await page.getByRole("button", { name: "Crear sucursal" }).click();
  await expect(page).toHaveURL(new RegExp(`/sucursales/${createdBranchId}$`));
  await expect(
    page.getByRole("heading", { name: "Sucursal E2E", exact: true }),
  ).toBeVisible();
  expect(createRequests).toBe(1);
  expect(createBody).toEqual({ code: "E2E_NORTH", name: "Sucursal E2E" });
  await expect(
    page.getByRole("link", { name: "Editar sucursal" }),
  ).toBeVisible();
  await waitForVisualTransitions(page);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

  await page.getByRole("link", { name: "Editar sucursal" }).click();
  await expect(page).toHaveURL(
    new RegExp(`/sucursales/${createdBranchId}/editar$`),
  );
  await expect(page.getByText("Código:")).toContainText("E2E_NORTH");
  await expect(
    page.getByRole("textbox", { name: "Código de la sucursal" }),
  ).toHaveCount(0);
  await page
    .getByRole("textbox", { name: "Nombre de la sucursal" })
    .fill("Sucursal E2E actualizada");
  await page.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(page).toHaveURL(new RegExp(`/sucursales/${createdBranchId}$`));
  await expect(
    page.getByRole("heading", {
      name: "Sucursal E2E actualizada",
      exact: true,
    }),
  ).toBeVisible();
  expect(updateBody).toMatchObject({
    name: "Sucursal E2E actualizada",
    version: 0,
  });
  expect(updateBody).not.toHaveProperty("code");
  expect(updateBody).not.toHaveProperty("status");

  const widths = await page.evaluate(() => ({
    document: document.documentElement.scrollWidth,
    viewport: window.innerWidth,
  }));
  expect(widths.document).toBeLessThanOrEqual(widths.viewport);
});

test("deactivating the selected branch reconciles the canonical context in every tab", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const selectedBranch = {
    id: branchId,
    organizationId,
    code: "NORTH",
    name: "Sucursal Norte",
    timezone: "America/El_Salvador",
    initialBranch: false,
  };
  const fallbackBranch = {
    ...selectedBranch,
    id: "00000000-0000-4000-8000-000000000022",
    code: "SOUTH",
    name: "Sucursal Sur",
  };
  let currentIdentity = {
    ...identity("ADMIN", "ORGANIZATION"),
    activeBranch: selectedBranch,
    availableBranches: [selectedBranch, fallbackBranch],
  };
  let currentBranch = branchRecord({
    status: "ACTIVE",
    initialBranch: false,
    version: 7,
  });
  let statusBody: Record<string, unknown> | undefined;
  let statusRequests = 0;

  await page.context().route("**/api/v1/auth/me", async (route) => {
    await route.fulfill({ status: 200, json: currentIdentity });
  });
  await page.context().route("**/api/v1/me/profile", async (route) => {
    await route.fulfill({
      status: 200,
      json: {
        userId: currentIdentity.id,
        username: currentIdentity.username,
        email: "operator@example.test",
        firstName: "Operador",
        lastName: "Prueba",
        displayName: currentIdentity.fullName,
        roles: currentIdentity.roles,
        status: "ACTIVE",
        photoPresent: false,
        photoUrl: null,
        version: 1,
      },
    });
  });
  await page.context().route("**/api/v1/auth/csrf", async (route) => {
    await route.fulfill({
      status: 200,
      json: {
        token: "synthetic-branch-status-csrf",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      },
    });
  });
  await page.context().route("**/api/v1/branches/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    if (
      url.pathname === `/api/v1/branches/${branchId}/deactivate` &&
      request.method() === "POST"
    ) {
      statusRequests += 1;
      statusBody = request.postDataJSON() as Record<string, unknown>;
      expect(request.headers()["x-xsrf-token"]).toBe(
        "synthetic-branch-status-csrf",
      );
      currentBranch = branchRecord({
        status: "INACTIVE",
        initialBranch: false,
        version: 8,
      });
      currentIdentity = {
        ...currentIdentity,
        activeBranch: fallbackBranch,
        availableBranches: [fallbackBranch],
      };
      await route.fulfill({ status: 200, json: currentBranch });
      return;
    }
    if (url.pathname === `/api/v1/branches/${branchId}`) {
      await route.fulfill({ status: 200, json: currentBranch });
      return;
    }
    await route.fulfill({ status: 404 });
  });

  await page.goto(`/sucursales/${branchId}`);
  await expect(
    page.getByRole("heading", { name: "Sucursal Norte", exact: true }),
  ).toBeVisible();
  const otherTab = await page.context().newPage();
  await otherTab.goto("/");
  const otherTabContext = otherTab.locator(
    '[data-branch-context-control="header"]',
  );
  await expect(otherTabContext).toContainText("Sucursal Norte · NORTH");

  await page.getByRole("button", { name: "Desactivar sucursal" }).click();
  const dialog = page.getByRole("dialog", {
    name: "Desactivar sucursal: Sucursal Norte",
  });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText(/Esta es tu sucursal operativa/);
  await dialog
    .getByRole("textbox", { name: "Motivo requerido" })
    .fill("Reorganización operativa aprobada");
  await waitForVisualTransitions(page);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await dialog.getByRole("button", { name: "Confirmar desactivación" }).click();

  await expect(page.getByText("Estado actualizado")).toBeVisible();
  await expect(
    page.locator('[data-branch-context-control="header"]'),
  ).toContainText("Sucursal Sur · SOUTH");
  await expect(otherTabContext).toContainText("Sucursal Sur · SOUTH");
  await expect(page.getByText(/está inactiva/i)).toBeVisible();
  expect(statusRequests).toBe(1);
  expect(statusBody).toEqual({
    reason: "Reorganización operativa aprobada",
    version: 7,
  });
  await waitForVisualTransitions(page);
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  await otherTab.close();
});

test("an ADMIN with branch scope cannot open the branch editor or trigger branch reads", async ({
  page,
}) => {
  await installReadSession(page, "ADMIN", "BRANCH");
  let branchReads = 0;
  await page.route(`**/api/v1/branches/${branchId}`, async (route) => {
    branchReads += 1;
    await route.fulfill({ status: 200, json: branchRecord() });
  });

  await page.goto(`/sucursales/${branchId}/editar`);
  await expect(page.getByText(/requiere una cuenta ADMIN/i)).toBeVisible();
  expect(branchReads).toBe(0);
});
