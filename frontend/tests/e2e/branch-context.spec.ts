import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";
import type { BrowserContext, Page } from "@playwright/test";

const branchA = {
  id: "00000000-0000-4000-8000-000000000002",
  organizationId: "00000000-0000-4000-8000-000000000001",
  code: "TEST",
  name: "Sucursal de prueba",
  timezone: "America/El_Salvador",
  initialBranch: true,
};

const branchB = {
  ...branchA,
  id: "00000000-0000-4000-8000-000000000004",
  code: "SECOND",
  name: "Sucursal norte",
  initialBranch: false,
};

type StaffRole = "ADMIN" | "RECEPTIONIST";
type OrganizationScope = "ORGANIZATION" | "BRANCH";

function identity(
  role: StaffRole = "ADMIN",
  scope: OrganizationScope = "ORGANIZATION",
  overrides: {
    activeBranch?: typeof branchA | null;
    availableBranches?: (typeof branchA)[];
  } = {},
) {
  const availableBranches = overrides.availableBranches ?? [branchA, branchB];
  return {
    id: "00000000-0000-4000-8000-000000000003",
    username: "operador-prueba",
    fullName: "Operador de prueba",
    roles: [role],
    organizationScope: scope,
    activeBranch:
      overrides.activeBranch === undefined ? branchA : overrides.activeBranch,
    availableBranches,
    passwordChangeRequired: false,
  };
}

function profileFor(current: ReturnType<typeof identity>) {
  return {
    userId: current.id,
    username: current.username,
    email: "operador@example.test",
    firstName: "Ana",
    lastName: "Ríos",
    displayName: current.fullName,
    roles: current.roles,
    status: "ACTIVE",
    photoPresent: false,
    photoUrl: null,
    version: 1,
  };
}

async function installContextRoutes(
  context: BrowserContext,
  initialIdentity: ReturnType<typeof identity>,
) {
  let current = initialIdentity;
  const requests = {
    catalog: 0,
    csrf: 0,
    select: [] as unknown[],
    clear: 0,
  };

  await context.route("**/api/v1/auth/me", async (route) => {
    await route.fulfill({ status: 200, json: current });
  });
  await context.route("**/api/v1/me/profile", async (route) => {
    await route.fulfill({ status: 200, json: profileFor(current) });
  });
  await context.route("**/api/v1/auth/csrf", async (route) => {
    requests.csrf += 1;
    await route.fulfill({
      status: 200,
      json: {
        token: "synthetic-branch-context-csrf",
        headerName: "X-XSRF-TOKEN",
        parameterName: "_csrf",
      },
    });
  });
  await context.route("**/api/v1/branches", async (route) => {
    requests.catalog += 1;
    await route.fulfill({ status: 500, body: "Unexpected catalog request" });
  });
  await context.route("**/api/v1/me/branch-context", async (route) => {
    const request = route.request();
    if (request.method() === "PUT") {
      const body = request.postDataJSON() as { branchId: string };
      requests.select.push(body);
      if (
        request.headers()["x-xsrf-token"] !== "synthetic-branch-context-csrf"
      ) {
        await route.fulfill({ status: 403, body: "Missing CSRF" });
        return;
      }
      const selected = current.availableBranches.find(
        ({ id }) => id === body.branchId,
      );
      if (!selected) {
        await route.fulfill({ status: 409, body: "ACTIVE_BRANCH_UNAVAILABLE" });
        return;
      }
      current = { ...current, activeBranch: selected };
      await route.fulfill({
        status: 200,
        json: {
          organizationId: selected.organizationId,
          scopeType: current.organizationScope,
          activeBranch: selected,
          availableBranches: current.availableBranches,
        },
      });
      return;
    }
    if (request.method() === "DELETE") {
      requests.clear += 1;
      if (
        request.headers()["x-xsrf-token"] !== "synthetic-branch-context-csrf"
      ) {
        await route.fulfill({ status: 403, body: "Missing CSRF" });
        return;
      }
      current = {
        ...current,
        activeBranch:
          current.availableBranches.length === 1
            ? (current.availableBranches[0] ?? null)
            : null,
      };
      await route.fulfill({ status: 204 });
      return;
    }
    await route.fulfill({ status: 405 });
  });

  return {
    requests,
    getCurrentIdentity: () => current,
  };
}

function headerSelector(page: Page) {
  return page
    .locator('[data-branch-context-control="header"]')
    .getByRole("combobox", { name: "Sucursal operativa" });
}

for (const [role, scope] of [
  ["ADMIN", "ORGANIZATION"],
  ["ADMIN", "BRANCH"],
  ["RECEPTIONIST", "BRANCH"],
] as const) {
  test(`${role} ${scope} cambia solo a una sucursal autorizada`, async ({
    page,
  }) => {
    const api = await installContextRoutes(
      page.context(),
      identity(role, scope),
    );
    await page.goto("/");
    const selector = headerSelector(page);
    await expect(selector).toBeVisible();
    await selector.click();
    await expect(
      page.getByRole("option", { name: "Sucursal norte · SECOND" }),
    ).toBeVisible();
    await expect(
      page.getByRole("option", { name: /todas las sucursales/i }),
    ).toHaveCount(0);
    await page.getByRole("option", { name: "Sucursal norte · SECOND" }).click();

    await expect(
      page.locator('[data-branch-context-control="header"]'),
    ).toContainText("Sucursal norte · SECOND");
    await expect(page.getByText("Sucursal: Sucursal norte")).toBeVisible();
    expect(api.requests.select).toEqual([{ branchId: branchB.id }]);
    expect(api.requests.csrf).toBe(1);
    expect(api.requests.catalog).toBe(0);
  });
}

test("limpiar y recargar conserva el contexto confirmado por el servidor", async ({
  page,
}) => {
  const api = await installContextRoutes(page.context(), identity());
  await page.goto("/");
  await page
    .locator('[data-branch-context-control="header"]')
    .getByRole("button", { name: "Quitar selección" })
    .click();

  await expect(
    page.locator('[data-branch-context-control="header"]'),
  ).toContainText("Sin sucursal activa");
  expect(api.requests.clear).toBe(1);
  expect(api.requests.csrf).toBe(1);
  expect(api.getCurrentIdentity().activeBranch).toBeNull();

  await page.reload();
  await expect(headerSelector(page)).toBeVisible();
  await expect(
    page.locator('[data-branch-context-control="header"]'),
  ).toContainText("Sin sucursal activa");
  expect(api.getCurrentIdentity().activeBranch).toBeNull();
});

test("la selección automática del servidor con una sucursal no se puede limpiar", async ({
  page,
}) => {
  const api = await installContextRoutes(
    page.context(),
    identity("RECEPTIONIST", "BRANCH", {
      activeBranch: branchA,
      availableBranches: [branchA],
    }),
  );
  await page.goto("/");

  await expect(headerSelector(page)).toBeDisabled();
  await expect(
    page.locator('[data-branch-context-control="header"]'),
  ).toContainText("Sucursal de prueba · TEST");
  await expect(
    page.locator('[data-branch-context-control="header"]').getByRole("button", {
      name: "Quitar selección",
    }),
  ).toHaveCount(0);
  expect(api.requests.clear).toBe(0);
});

test("el estado sin sucursales no inventa opciones de alcance global", async ({
  page,
}) => {
  const api = await installContextRoutes(
    page.context(),
    identity("ADMIN", "ORGANIZATION", {
      activeBranch: null,
      availableBranches: [],
    }),
  );
  await page.goto("/");

  await expect(
    page.locator('[data-branch-context-control="header"]'),
  ).toContainText(
    "Ámbito de organización · Sin sucursales operativas disponibles",
  );
  await expect(headerSelector(page)).toHaveCount(0);
  await expect(
    page.getByRole("option", { name: /todas las sucursales/i }),
  ).toHaveCount(0);
  expect(api.requests.catalog).toBe(0);
});

test("el selector funciona con teclado, movimiento reducido y viewport estrecho", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 620 });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await installContextRoutes(
    page.context(),
    identity("ADMIN", "ORGANIZATION", {
      activeBranch: null,
      availableBranches: [
        {
          ...branchA,
          code: "CENTRAL-2026",
          name: "Sucursal central con nombre largo para comprobar que el menú se adapte",
        },
        {
          ...branchB,
          code: "NORTE-2026",
          name: "Sucursal del sector norte con una descripción extensa",
        },
      ],
    }),
  );
  await page.goto("/");

  const selector = headerSelector(page);
  await selector.focus();
  await selector.press("ArrowDown");
  await expect(selector).toHaveAttribute("aria-expanded", "true");
  await expect(
    page.getByRole("option", {
      name: "Sucursal del sector norte con una descripción extensa · NORTE-2026",
    }),
  ).toBeVisible();
  await selector.press("Escape");
  await expect(selector).toHaveAttribute("aria-expanded", "false");
  await expect(selector).toBeFocused();

  const viewport = await page.evaluate(() => ({
    width: window.innerWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }));
  expect(viewport.scrollWidth).toBeLessThanOrEqual(viewport.width);
  const audit = await new AxeBuilder({ page }).analyze();
  expect(audit.violations).toEqual([]);
});

test("las pestañas de la misma sesión comparten la sucursal activa", async ({
  page,
}) => {
  await installContextRoutes(page.context(), identity());
  await page.goto("/");
  const otherTab = await page.context().newPage();
  await otherTab.goto("/");

  const selector = headerSelector(page);
  await selector.click();
  await page.getByRole("option", { name: "Sucursal norte · SECOND" }).click();

  await expect(
    otherTab.locator('[data-branch-context-control="header"]'),
  ).toContainText("Sucursal norte · SECOND");
  await otherTab.close();
});
