import AxeBuilder from "@axe-core/playwright";
import type { Page } from "@playwright/test";
import { waitForVisualTransitions } from "../helpers/visual-stability";
import {
  createStaffFixture,
  expect,
  integrationBranchIds,
  test,
  type StaffFixture,
} from "./fixtures";

const baseUrl = "http://127.0.0.1:5183";
const lifecycleReason = "Reorganización operativa sintética.";

// These cases share mutable branch fixtures; preserve their order if workers change.
test.describe.configure({ mode: "serial" });

interface BranchApiRecord {
  id: string;
  code: string;
  name: string;
  timezone: string;
  status: "ACTIVE" | "INACTIVE";
  initialBranch: boolean;
  version: number;
}

interface CurrentUserRecord {
  activeBranch: Pick<BranchApiRecord, "id" | "code" | "name"> | null;
  availableBranches: Array<Pick<BranchApiRecord, "id" | "code" | "name">>;
}

interface BrowserApiResult {
  status: number;
  payload: unknown;
}

function requiredEnvironmentValue(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error("The owned F03 fixture is incomplete.");
  return value;
}

async function signIn(page: Page, account: StaffFixture): Promise<void> {
  await page.goto(`${baseUrl}/`);
  await expect(
    page.getByRole("heading", { level: 1, name: "Iniciar sesión" }),
  ).toBeVisible();
  await page
    .getByRole("textbox", { name: "Usuario o correo" })
    .fill(account.username);
  await page.getByLabel("Contraseña").fill(account.password);
  await page.getByRole("button", { name: "Iniciar sesión" }).click();
  await expect(
    page.getByRole("heading", { level: 1, name: "Inicio" }),
  ).toBeVisible();
}

async function apiRequest(
  page: Page,
  path: string,
  method: "GET" | "POST" | "PUT" | "DELETE" = "GET",
  body?: Record<string, unknown>,
  csrfMode: "valid" | "invalid" = "valid",
): Promise<BrowserApiResult> {
  return page.evaluate(
    async ({ path, method, body, csrfMode }) => {
      const headers = new Headers({ Accept: "application/json" });
      if (body) headers.set("Content-Type", "application/json");
      if (method !== "GET") {
        const csrfResponse = await fetch("/api/v1/auth/csrf", {
          credentials: "same-origin",
          cache: "no-store",
        });
        if (!csrfResponse.ok) {
          return { status: csrfResponse.status, payload: null };
        }
        const csrf = (await csrfResponse.json()) as {
          token: string;
          headerName: string;
        };
        headers.set(
          csrf.headerName,
          csrfMode === "invalid" ? "invalid-synthetic-csrf" : csrf.token,
        );
      }
      const response = await fetch(path, {
        method,
        credentials: "same-origin",
        cache: "no-store",
        headers,
        ...(body ? { body: JSON.stringify(body) } : {}),
      });
      const raw = await response.text();
      let payload: unknown = null;
      try {
        payload = raw ? JSON.parse(raw) : null;
      } catch {
        // Non-JSON error bodies are intentionally discarded.
      }
      return { status: response.status, payload };
    },
    { path, method, body, csrfMode },
  );
}

async function readBranch(
  page: Page,
  branchId: string,
): Promise<BranchApiRecord> {
  const result = await apiRequest(page, `/api/v1/branches/${branchId}`);
  expect(result.status).toBe(200);
  return result.payload as BranchApiRecord;
}

async function readCurrentUser(page: Page): Promise<CurrentUserRecord> {
  const result = await apiRequest(page, "/api/v1/auth/me");
  expect(result.status).toBe(200);
  return result.payload as CurrentUserRecord;
}

async function ensureBranchActive(page: Page, branchId: string): Promise<void> {
  const branch = await readBranch(page, branchId);
  if (branch.status === "ACTIVE") return;

  const result = await apiRequest(
    page,
    `/api/v1/branches/${branchId}/activate`,
    "POST",
    { reason: lifecycleReason, version: branch.version },
  );
  expect(result.status).toBe(200);
}

async function expectNoAxeViolations(page: Page): Promise<void> {
  await waitForVisualTransitions(page);
  const result = await new AxeBuilder({ page }).analyze();
  expect(result.violations.map(({ id }) => id)).toEqual([]);
}

async function confirmStatusChange(
  page: Page,
  target: "ACTIVE" | "INACTIVE",
  reason: string,
  validateRequiredReason = false,
): Promise<void> {
  const action = target === "ACTIVE" ? "Activar" : "Desactivar";
  const dialogName =
    target === "ACTIVE" ? /Activar sucursal:/ : /Desactivar sucursal:/;
  await page.getByRole("button", { name: `${action} sucursal` }).click();
  const dialog = page.getByRole("dialog", { name: dialogName });
  await expect(dialog).toBeVisible();
  if (validateRequiredReason) {
    await dialog
      .getByRole("button", {
        name:
          target === "ACTIVE"
            ? "Confirmar activación"
            : "Confirmar desactivación",
      })
      .click();
    await expect(
      dialog.getByRole("alert").getByText("Escribe un motivo para continuar."),
    ).toBeVisible();
  }
  await dialog.getByRole("textbox", { name: "Motivo requerido" }).fill(reason);
  await expectNoAxeViolations(page);
  await dialog
    .getByRole("button", {
      name:
        target === "ACTIVE"
          ? "Confirmar activación"
          : "Confirmar desactivación",
    })
    .click();
  await expect(
    page.getByText("Estado actualizado", { exact: true }),
  ).toBeVisible();
}

function branchSelector(page: Page) {
  return page
    .locator('[data-branch-context-control="header"]')
    .getByRole("combobox", { name: "Sucursal operativa" });
}

async function selectBranch(
  page: Page,
  branch: BranchApiRecord,
): Promise<void> {
  await branchSelector(page).click();
  await page
    .getByRole("option", { name: `${branch.name} · ${branch.code}` })
    .click();
  await expect(
    page.locator('[data-branch-context-control="header"]'),
  ).toContainText(`${branch.name} · ${branch.code}`);
}

test("P03 P04 P15 P16 P17 real organization branch administration uses canonical data", async ({
  page,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  await signIn(page, account);

  await page.goto("/organizacion");
  await expect(
    page.getByRole("heading", { level: 1, name: "Organización" }),
  ).toBeVisible();
  await expectNoAxeViolations(page);

  const inactiveBranchId = integrationBranchIds.inactive();
  const inactiveBranchName = requiredEnvironmentValue(
    "CG_E2E_INACTIVE_BRANCH_NAME",
  );
  const inactiveBranch = await readBranch(page, inactiveBranchId);
  expect(inactiveBranch.status).toBe("INACTIVE");

  await page.goto("/sucursales");
  const catalogHeading = page.locator("#main-content h1#main-heading");
  await expect(catalogHeading).toHaveCount(1);
  await expect(catalogHeading).toHaveText("Sucursales");
  await expect(catalogHeading).toBeVisible();
  await expect(
    page.getByText(inactiveBranchName, { exact: true }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: `Ver detalle de ${inactiveBranchName}` })
    .click();
  await expect(
    page.getByRole("heading", { name: inactiveBranchName }),
  ).toBeVisible();
  await expect(page.getByText(/está inactiva/i)).toBeVisible();

  await confirmStatusChange(page, "ACTIVE", lifecycleReason, true);
  expect((await readBranch(page, inactiveBranchId)).status).toBe("ACTIVE");
  await expectNoAxeViolations(page);
  await confirmStatusChange(page, "INACTIVE", lifecycleReason);
  expect((await readBranch(page, inactiveBranchId)).status).toBe("INACTIVE");

  const initialBranch = await readBranch(page, integrationBranchIds.initial());
  expect(initialBranch.initialBranch).toBe(true);
  const secondBranch = await readBranch(page, integrationBranchIds.second());
  expect(secondBranch.status).toBe("ACTIVE");

  try {
    await page.goto(`/sucursales/${secondBranch.id}`);
    await expect(
      page.getByRole("heading", { name: secondBranch.name }),
    ).toBeVisible();
    await confirmStatusChange(page, "INACTIVE", lifecycleReason);
    expect((await readBranch(page, secondBranch.id)).status).toBe("INACTIVE");

    const initialProtection = await apiRequest(
      page,
      `/api/v1/branches/${initialBranch.id}/deactivate`,
      "POST",
      {
        reason: lifecycleReason,
        version: (await readBranch(page, initialBranch.id)).version,
      },
    );
    expect(initialProtection.status).toBe(409);
    expect((await readBranch(page, initialBranch.id)).status).toBe("ACTIVE");
  } finally {
    await ensureBranchActive(page, initialBranch.id);
    await ensureBranchActive(page, secondBranch.id);
  }

  await page.goto("/sucursales");
  await page.getByRole("link", { name: "Nueva sucursal" }).click();
  const codeSuffix = crypto.randomUUID().replaceAll("-", "").slice(0, 10);
  const code = `f03_${codeSuffix}`;
  const originalName = "Sucursal de integración F03";
  await page.getByRole("textbox", { name: "Código de la sucursal" }).fill(code);
  await page
    .getByRole("textbox", { name: "Nombre de la sucursal" })
    .fill(originalName);
  await page.getByRole("button", { name: "Crear sucursal" }).click();
  await expect(page.getByRole("heading", { name: originalName })).toBeVisible();

  const catalog = await apiRequest(page, "/api/v1/organization");
  expect(catalog.status).toBe(200);
  const defaultTimezone = (catalog.payload as { defaultTimezone: string })
    .defaultTimezone;
  const createdBranchId = new URL(page.url()).pathname.split("/").at(-1);
  expect(createdBranchId).toBeTruthy();
  const createdBranch = await readBranch(page, createdBranchId!);
  expect(createdBranch).toMatchObject({
    code: code.toUpperCase(),
    name: originalName,
    status: "ACTIVE",
    initialBranch: false,
    timezone: defaultTimezone,
    version: 0,
  });

  await page.getByRole("link", { name: "Editar sucursal" }).click();
  await page
    .getByRole("textbox", { name: "Nombre de la sucursal" })
    .fill("Sucursal F03 actualizada");
  await page.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(
    page.getByRole("heading", { name: "Sucursal F03 actualizada" }),
  ).toBeVisible();
  const editedBranch = await readBranch(page, createdBranch.id);
  expect(editedBranch).toMatchObject({
    code: code.toUpperCase(),
    name: "Sucursal F03 actualizada",
    version: 1,
    status: "ACTIVE",
  });

  const staleTransition = await apiRequest(
    page,
    `/api/v1/branches/${createdBranch.id}/deactivate`,
    "POST",
    { reason: lifecycleReason, version: 0 },
  );
  expect(staleTransition.status).toBe(409);
  expect((await readBranch(page, createdBranch.id)).status).toBe("ACTIVE");
  await confirmStatusChange(page, "INACTIVE", lifecycleReason);
  expect((await readBranch(page, createdBranch.id)).status).toBe("INACTIVE");
});

test("P07 P08 P09 P13 P18 real branch deactivation reconciles the session across tabs", async ({
  page,
  browser,
  database,
}) => {
  const account = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  await signIn(page, account);

  const secondBranchId = integrationBranchIds.second();
  const secondBranch = await readBranch(page, secondBranchId);
  expect(secondBranch.status).toBe("ACTIVE");
  await selectBranch(page, secondBranch);
  expect((await readCurrentUser(page)).activeBranch?.id).toBe(secondBranch.id);

  const otherTab = await page.context().newPage();
  await otherTab.goto("/");
  const otherTabContext = otherTab.locator(
    '[data-branch-context-control="header"]',
  );
  await expect(otherTabContext).toContainText(
    `${secondBranch.name} · ${secondBranch.code}`,
  );

  const separateAccount = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  const separateContext = await browser.newContext();
  const separatePage = await separateContext.newPage();
  try {
    await separatePage.goto(`${baseUrl}/`);
    await signIn(separatePage, separateAccount);
    expect((await readCurrentUser(separatePage)).activeBranch).toBeNull();

    const initialBranch = await readBranch(
      page,
      integrationBranchIds.initial(),
    );
    await page.goto(`/sucursales/${secondBranch.id}`);
    await expect(
      page.getByRole("heading", { name: secondBranch.name }),
    ).toBeVisible();
    await confirmStatusChange(page, "INACTIVE", lifecycleReason);

    await expect(
      page.locator('[data-branch-context-control="header"]'),
    ).toContainText(`${initialBranch.name} · ${initialBranch.code}`);
    await expect(otherTabContext).toContainText(
      `${initialBranch.name} · ${initialBranch.code}`,
    );
    expect((await readCurrentUser(page)).activeBranch?.id).toBe(
      initialBranch.id,
    );
    expect((await readCurrentUser(separatePage)).activeBranch?.id).toBe(
      initialBranch.id,
    );

    await confirmStatusChange(page, "ACTIVE", lifecycleReason);
    expect((await readBranch(page, secondBranch.id)).status).toBe("ACTIVE");
    await selectBranch(page, await readBranch(page, secondBranch.id));
    await expect(otherTabContext).toContainText(
      `${secondBranch.name} · ${secondBranch.code}`,
    );
    const storageContainsBranchId = await page.evaluate((branchId) => {
      const values = [localStorage, sessionStorage].flatMap((storage) =>
        Array.from({ length: storage.length }, (_, index) =>
          storage.getItem(storage.key(index) ?? ""),
        ).filter((value): value is string => value !== null),
      );
      return values.some((value) => value.includes(branchId));
    }, secondBranch.id);
    expect(storageContainsBranchId).toBe(false);

    const clearResponsePromise = page.waitForResponse((response) => {
      const request = response.request();
      return (
        request.method() === "DELETE" &&
        new URL(response.url()).pathname === "/api/v1/me/branch-context"
      );
    });
    await page
      .locator('[data-branch-context-control="header"]')
      .getByRole("button", { name: "Quitar selección" })
      .click();
    expect((await clearResponsePromise).status()).toBe(204);
    await expect(
      page.locator('[data-branch-context-control="header"]'),
    ).toContainText("Sin sucursal activa");
    await expect(otherTabContext).toContainText("Sin sucursal activa");
    expect((await readCurrentUser(page)).activeBranch).toBeNull();
    await selectBranch(page, await readBranch(page, secondBranch.id));
    await expect(otherTabContext).toContainText(
      `${secondBranch.name} · ${secondBranch.code}`,
    );
    expect((await readCurrentUser(separatePage)).activeBranch).toBeNull();
    await expectNoAxeViolations(page);
  } finally {
    await separateContext.close();
    await otherTab.close();
  }
});

test("P02 P07 P19 real branch authorization and session boundaries remain server enforced", async ({
  browser,
  database,
}) => {
  const secondBranchId = integrationBranchIds.second();

  for (const role of ["ADMIN", "RECEPTIONIST"] as const) {
    const account = await createStaffFixture(database, {
      role,
      scope: "BRANCH",
      branchAssignments: role === "ADMIN" ? 1 : 2,
    });
    const context = await browser.newContext();
    const page = await context.newPage();
    try {
      await page.goto(`${baseUrl}/`);
      await signIn(page, account);
      const branch = await readBranch(page, secondBranchId);
      const deniedWrite = await apiRequest(
        page,
        `/api/v1/branches/${branch.id}/deactivate`,
        "POST",
        { reason: lifecycleReason, version: branch.version },
      );
      expect(deniedWrite.status).toBe(403);
      expect((await readBranch(page, branch.id)).status).toBe("ACTIVE");

      const contextSelection = await apiRequest(
        page,
        "/api/v1/me/branch-context",
        "PUT",
        { branchId: secondBranchId },
      );
      if (role === "ADMIN") {
        expect(contextSelection.status).toBe(409);
        await page.goto(`/sucursales/${branch.id}`);
        await expect(
          page.getByRole("button", { name: "Desactivar sucursal" }),
        ).toHaveCount(0);
      } else {
        expect(contextSelection.status).toBe(200);
        expect((await readCurrentUser(page)).activeBranch?.id).toBe(
          secondBranchId,
        );
        const inactiveSelection = await apiRequest(
          page,
          "/api/v1/me/branch-context",
          "PUT",
          { branchId: integrationBranchIds.inactive() },
        );
        expect(inactiveSelection.status).toBe(409);
      }
    } finally {
      await context.close();
    }
  }

  const anonymousContext = await browser.newContext();
  const anonymousPage = await anonymousContext.newPage();
  try {
    await anonymousPage.goto(`${baseUrl}/login`);
    const anonymousUser = await apiRequest(anonymousPage, "/api/v1/auth/me");
    expect(anonymousUser.status).toBe(401);
    const anonymousMutation = await apiRequest(
      anonymousPage,
      `/api/v1/branches/${secondBranchId}/deactivate`,
      "POST",
      { reason: lifecycleReason, version: 0 },
    );
    expect(anonymousMutation.status).toBe(401);
  } finally {
    await anonymousContext.close();
  }

  const adminAccount = await createStaffFixture(database, {
    role: "ADMIN",
    scope: "ORGANIZATION",
  });
  const adminContext = await browser.newContext();
  const adminPage = await adminContext.newPage();
  try {
    await adminPage.goto(`${baseUrl}/`);
    await signIn(adminPage, adminAccount);
    const branch = await readBranch(adminPage, secondBranchId);
    const rejectedCsrf = await apiRequest(
      adminPage,
      `/api/v1/branches/${branch.id}/deactivate`,
      "POST",
      { reason: lifecycleReason, version: branch.version },
      "invalid",
    );
    expect(rejectedCsrf.status).toBe(403);
    expect((await readBranch(adminPage, branch.id)).status).toBe("ACTIVE");
  } finally {
    await adminContext.close();
  }
});
